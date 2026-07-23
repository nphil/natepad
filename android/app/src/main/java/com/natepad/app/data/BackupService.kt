package com.natepad.app.data

import com.natepad.app.pgp.PgpService
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-encrypted backup of all keys.
 *
 * Container format (cross-platform — the iOS app reads/writes the same envelope):
 *   JSON { format, version, kdf, iterations, salt, nonce, ciphertext }
 *   - KDF:        PBKDF2-HMAC-SHA256, 600 000 iterations (OWASP 2023 rec), 32-byte key
 *   - Cipher:     AES-256-GCM, 12-byte nonce, 16-byte tag appended to ciphertext
 *                 (javax.crypto GCM output and CryptoKit ciphertext+tag are identical)
 *   - Plaintext:  JSON { keys: [{fingerprint, userIds, armoredPublic?, armoredPrivate?}] }
 */
object BackupService {

    const val FILE_EXTENSION = "natepadbackup"

    private const val FORMAT = "natepad-backup"
    private const val VERSION = 1
    private const val KDF = "pbkdf2-hmac-sha256"
    private const val ITERATIONS = 600_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128

    @Serializable
    private data class Envelope(
        val format: String,
        val version: Int,
        val kdf: String,
        val iterations: Int,
        val salt: String,
        val nonce: String,
        val ciphertext: String
    )

    @Serializable
    private data class BackupKey(
        val fingerprint: String,
        val userIds: List<String>,
        val armoredPublic: String? = null,
        val armoredPrivate: String? = null
    )

    @Serializable
    private data class Payload(val keys: List<BackupKey>)

    private val json = Json { ignoreUnknownKeys = true }

    /** Encrypts all keys into a portable backup container. Returns the JSON text. */
    fun createBackup(keys: List<KeyRecord>, password: CharArray): String {
        val payload = Payload(
            keys = keys.map { rec ->
                BackupKey(
                    fingerprint = rec.fingerprint,
                    userIds = rec.userIds,
                    armoredPublic = rec.armoredPublic.ifBlank { null },
                    armoredPrivate = rec.armoredPrivate.ifBlank { null }
                )
            }
        )
        val plaintext = json.encodeToString(payload).toByteArray(Charsets.UTF_8)

        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext)

        val b64 = Base64.getEncoder()
        val envelope = Envelope(
            format = FORMAT,
            version = VERSION,
            kdf = KDF,
            iterations = ITERATIONS,
            salt = b64.encodeToString(salt),
            nonce = b64.encodeToString(nonce),
            ciphertext = b64.encodeToString(ciphertext)
        )
        return json.encodeToString(envelope)
    }

    /**
     * Decrypts a backup container and returns the contained keys.
     * Throws on wrong password (GCM tag mismatch) or unrecognized format.
     */
    fun restoreBackup(text: String, password: CharArray): List<KeyRecord> {
        val envelope = try {
            json.decodeFromString<Envelope>(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not a NatePad backup file")
        }
        require(envelope.format == FORMAT) { "Not a NatePad backup file" }
        require(envelope.version == VERSION) { "Unsupported backup version ${envelope.version}" }
        require(envelope.kdf == KDF) { "Unsupported KDF ${envelope.kdf}" }

        val b64 = Base64.getDecoder()
        val salt = b64.decode(envelope.salt)
        val nonce = b64.decode(envelope.nonce)
        val ciphertext = b64.decode(envelope.ciphertext)

        val key = deriveKey(password, salt, envelope.iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        val plaintext = cipher.doFinal(ciphertext)  // throws AEADBadTagException on wrong password

        val payload = json.decodeFromString<Payload>(plaintext.toString(Charsets.UTF_8))
        return payload.keys.map { k ->
            val fp = k.fingerprint.uppercase()
            val base = KeyRecord(
                id = fp,
                fingerprint = fp,
                userIds = k.userIds,
                hasPublic = !k.armoredPublic.isNullOrBlank(),
                hasPrivate = !k.armoredPrivate.isNullOrBlank(),
                armoredPublic = k.armoredPublic ?: "",
                armoredPrivate = k.armoredPrivate ?: ""
            )
            // The backup payload carries only key material; creation and expiry
            // are re-derived from the key itself so restored records don't show
            // "created today" or miss expiration warnings.
            if (!base.hasPublic) base else runCatching {
                PgpService.parseKeys(base.armoredPublic)
                    .firstNotNullOfOrNull { PgpService.parsedKeyToRecord(it) }
            }.getOrNull()?.let { parsed ->
                base.copy(createdAt = parsed.createdAt, expiresAt = parsed.expiresAt)
            } ?: base
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }
}
