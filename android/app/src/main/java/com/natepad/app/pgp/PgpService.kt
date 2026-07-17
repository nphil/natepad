package com.natepad.app.pgp

import com.natepad.app.data.KeyRecord
import org.bouncycastle.bcpg.ArmoredInputStream
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.CompressionAlgorithmTags
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPCompressedDataGenerator
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPOnePassSignature
import org.bouncycastle.openpgp.PGPOnePassSignatureList
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureGenerator
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.util.Date

object PgpService {

    // ── Result types ──────────────────────────────────────────────────────────

    /** What we learned about an embedded signature while decrypting/verifying. */
    sealed class SignatureStatus {
        data object NotSigned : SignatureStatus()
        data class Valid(val signer: String?, val keyId: String) : SignatureStatus()
        data class UnknownKey(val keyId: String) : SignatureStatus()
        data class Invalid(val reason: String) : SignatureStatus()
    }

    data class DecryptionResult(val plaintext: String, val signature: SignatureStatus)

    /** A matching private key was found but no passphrase was supplied for it. */
    class PassphraseRequiredException(val record: KeyRecord) :
        Exception("Passphrase required for ${record.displayName}")

    /** The supplied passphrase failed to unlock the matching private key. */
    class WrongPassphraseException(val record: KeyRecord) :
        Exception("Wrong passphrase for ${record.displayName}")

    // ── Key generation ────────────────────────────────────────────────────────

    data class GeneratedKey(
        val armoredPublic: String,
        val armoredPrivate: String,
        val fingerprint: String,
        val userId: String
    )

    fun generateKeyPair(name: String, email: String, passphrase: String): GeneratedKey {
        val kpg = KeyPairGenerator.getInstance("RSA", "BC")
        kpg.initialize(4096, SecureRandom())
        val kp = kpg.generateKeyPair()

        val pgpKP = JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, kp, Date())
        val userId = if (email.isNotEmpty()) "$name <$email>" else name

        val digestCalc = JcaPGPDigestCalculatorProviderBuilder().setProvider("BC").build()
        val signerBuilder = JcaPGPContentSignerBuilder(
            pgpKP.publicKey.algorithm, HashAlgorithmTags.SHA256
        ).setProvider("BC")
        val encryptor = JcePBESecretKeyEncryptorBuilder(
            SymmetricKeyAlgorithmTags.AES_256,
            digestCalc.get(HashAlgorithmTags.SHA256)
        ).setProvider("BC").build(passphrase.toCharArray())

        val gen = PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION, pgpKP, userId,
            digestCalc.get(HashAlgorithmTags.SHA1), null, null, signerBuilder, encryptor
        )

        val secretRing = gen.generateSecretKeyRing()
        val publicRing = gen.generatePublicKeyRing()

        val armoredPub = ByteArrayOutputStream().also { buf ->
            ArmoredOutputStream(buf).use { publicRing.encode(it) }
        }.toString(Charsets.UTF_8)

        val armoredPriv = ByteArrayOutputStream().also { buf ->
            ArmoredOutputStream(buf).use { secretRing.encode(it) }
        }.toString(Charsets.UTF_8)

        val fp = publicRing.publicKey.fingerprint.joinToString("") { "%02X".format(it) }
        return GeneratedKey(armoredPub, armoredPriv, fp, userId)
    }

    // ── Key parsing ───────────────────────────────────────────────────────────

    data class ParsedKey(
        val publicRing: PGPPublicKeyRing?,
        val secretRing: PGPSecretKeyRing?
    )

    fun parseKeys(armored: String): List<ParsedKey> {
        val results = mutableListOf<ParsedKey>()
        runCatching {
            val decoded = PGPUtil.getDecoderStream(ByteArrayInputStream(armored.toByteArray()))
            val factory = PGPObjectFactory(decoded, JcaKeyFingerprintCalculator())
            var obj = factory.nextObject()
            while (obj != null) {
                when (obj) {
                    is PGPPublicKeyRing -> results.add(ParsedKey(obj, null))
                    is PGPSecretKeyRing -> results.add(ParsedKey(null, obj))
                    else -> {}
                }
                obj = factory.nextObject()
            }
        }
        return results
    }

    fun parsedKeyToRecord(parsed: ParsedKey): KeyRecord? {
        val secretRing = parsed.secretRing

        // Extract public ring from secret ring if no separate public ring
        val effectivePubRing: PGPPublicKeyRing = parsed.publicRing
            ?: secretRing?.let { sr ->
                val pubKeys = sr.secretKeys.asSequence().map { it.publicKey }.toList()
                if (pubKeys.isEmpty()) return null
                PGPPublicKeyRing(pubKeys)
            }
            ?: return null

        val masterKey = effectivePubRing.publicKey
        val fp = masterKey.fingerprint.joinToString("") { "%02X".format(it) }
        val userIds = masterKey.userIDs.asSequence().toList()

        val armoredPub = ByteArrayOutputStream().also { buf ->
            ArmoredOutputStream(buf).use { effectivePubRing.encode(it) }
        }.toString(Charsets.UTF_8)

        val armoredPriv = secretRing?.let { sr ->
            ByteArrayOutputStream().also { buf ->
                ArmoredOutputStream(buf).use { sr.encode(it) }
            }.toString(Charsets.UTF_8)
        } ?: ""

        return KeyRecord(
            id = fp,
            fingerprint = fp,
            userIds = userIds.ifEmpty { listOf("Unknown Key") },
            hasPublic = true,
            hasPrivate = secretRing != null,
            armoredPublic = armoredPub,
            armoredPrivate = armoredPriv,
            createdAt = masterKey.creationTime.time
        )
    }

    // ── Encrypt ───────────────────────────────────────────────────────────────

    fun encrypt(
        plaintext: String,
        recipients: List<KeyRecord>,
        signer: KeyRecord? = null,
        passphrase: String? = null
    ): String {
        require(recipients.isNotEmpty()) { "At least one recipient required" }

        val out = ByteArrayOutputStream()
        ArmoredOutputStream(out).use { armored ->

            val encGen = PGPEncryptedDataGenerator(
                org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder(
                    SymmetricKeyAlgorithmTags.AES_256
                ).setWithIntegrityPacket(true).setSecureRandom(SecureRandom()).setProvider("BC")
            )

            for (rec in recipients) {
                val encKey = extractEncryptionKey(rec.armoredPublic)
                    ?: throw PGPException("No encryption key found for ${rec.displayName}")
                encGen.addMethod(
                    JcePublicKeyKeyEncryptionMethodGenerator(encKey).setProvider("BC")
                )
            }

            encGen.open(armored, ByteArray(1 shl 16)).use { encOut ->
                PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP).open(encOut).use { compOut ->
                    if (signer != null && passphrase != null) {
                        val secretRing = loadSecretRing(signer.armoredPrivate)
                        val sigKey = secretRing.secretKey
                        val privKey = sigKey.extractPrivateKey(
                            JcePBESecretKeyDecryptorBuilder().setProvider("BC")
                                .build(passphrase.toCharArray())
                        )
                        val sigGen = PGPSignatureGenerator(
                            JcaPGPContentSignerBuilder(sigKey.publicKey.algorithm, HashAlgorithmTags.SHA256)
                                .setProvider("BC")
                        )
                        sigGen.init(PGPSignature.BINARY_DOCUMENT, privKey)
                        sigGen.generateOnePassVersion(false).encode(compOut)

                        val litGen = PGPLiteralDataGenerator()
                        val data = plaintext.toByteArray(Charsets.UTF_8)
                        litGen.open(compOut, PGPLiteralData.BINARY, "", data.size.toLong(), Date()).use { litOut ->
                            litOut.write(data)
                            sigGen.update(data)
                        }
                        sigGen.generate().encode(compOut)
                    } else {
                        val litGen = PGPLiteralDataGenerator()
                        val data = plaintext.toByteArray(Charsets.UTF_8)
                        litGen.open(compOut, PGPLiteralData.BINARY, "", data.size.toLong(), Date()).use {
                            it.write(data)
                        }
                    }
                }
            }
        }
        return out.toString(Charsets.UTF_8)
    }

    // ── Decrypt ───────────────────────────────────────────────────────────────

    /**
     * Decrypts a message and verifies any embedded one-pass signature against
     * [verificationKeys]. Throws [PassphraseRequiredException] when a matching
     * private key needs a passphrase the provider didn't supply, so the UI can
     * prompt for exactly the right key.
     */
    fun decrypt(
        armoredMessage: String,
        keys: List<KeyRecord>,
        verificationKeys: List<KeyRecord> = emptyList(),
        passphraseProvider: (KeyRecord) -> String?
    ): DecryptionResult {
        val decoded = PGPUtil.getDecoderStream(
            ByteArrayInputStream(armoredMessage.toByteArray(Charsets.UTF_8))
        )
        val pgpFactory = PGPObjectFactory(decoded, JcaKeyFingerprintCalculator())
        val encDataList = when (val o = pgpFactory.nextObject()) {
            is PGPEncryptedDataList -> o
            else -> pgpFactory.nextObject() as? PGPEncryptedDataList
                ?: throw PGPException("No encrypted data found")
        }

        var pendingPassphrase: KeyRecord? = null

        for (encData in encDataList.encryptedDataObjects) {
            if (encData !is PGPPublicKeyEncryptedData) continue
            val keyId = encData.keyID
            val matchingKey = keys.firstOrNull { rec ->
                rec.hasPrivate && runCatching {
                    loadSecretRing(rec.armoredPrivate).secretKeys.asSequence().any { it.keyID == keyId }
                }.getOrDefault(false)
            } ?: continue

            val secretKey = loadSecretRing(matchingKey.armoredPrivate)
                .secretKeys.asSequence().first { it.keyID == keyId }

            val passphrase = passphraseProvider(matchingKey)
            val privKey = if (passphrase == null) {
                // No passphrase supplied — works only for unprotected keys.
                try {
                    secretKey.extractPrivateKey(
                        JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(CharArray(0))
                    )
                } catch (e: PGPException) {
                    pendingPassphrase = matchingKey
                    continue
                }
            } else {
                try {
                    secretKey.extractPrivateKey(
                        JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(passphrase.toCharArray())
                    )
                } catch (e: PGPException) {
                    throw WrongPassphraseException(matchingKey)
                }
            }

            val clearStream = encData.getDataStream(
                JcePublicKeyDataDecryptorFactoryBuilder().setProvider("BC").build(privKey)
            )
            val result = readSignedLiteral(clearStream, verificationKeys)
            if (encData.isIntegrityProtected && !encData.verify()) {
                throw PGPException("Message failed its integrity check — it may have been tampered with")
            }
            return result
        }

        pendingPassphrase?.let { throw PassphraseRequiredException(it) }
        throw PGPException("Could not decrypt — no private key in your keyring matches this message")
    }

    // ── Sign ──────────────────────────────────────────────────────────────────

    fun sign(plaintext: String, key: KeyRecord, passphrase: String): String {
        val secretRing = loadSecretRing(key.armoredPrivate)
        val signingKey = secretRing.secretKey
        val privKey = signingKey.extractPrivateKey(
            JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(passphrase.toCharArray())
        )

        val sigGen = PGPSignatureGenerator(
            JcaPGPContentSignerBuilder(signingKey.publicKey.algorithm, HashAlgorithmTags.SHA256)
                .setProvider("BC")
        )
        sigGen.init(PGPSignature.CANONICAL_TEXT_DOCUMENT, privKey)

        val out = ByteArrayOutputStream()
        ArmoredOutputStream(out).use { armored ->
            armored.beginClearText(HashAlgorithmTags.SHA256)
            val data = plaintext.toByteArray(Charsets.UTF_8)
            armored.write(data)
            sigGen.update(data)
            armored.endClearText()
            val bcpgOut = org.bouncycastle.bcpg.BCPGOutputStream(armored)
            sigGen.generate().encode(bcpgOut)
            bcpgOut.finish()
        }
        return out.toString(Charsets.UTF_8)
    }

    // ── Verify ────────────────────────────────────────────────────────────────

    /**
     * Verifies a signed message and returns its text. Handles both cleartext
     * SIGNED MESSAGE blocks (this app's Sign output, GnuPG --clearsign) and
     * armored PGP MESSAGE blocks carrying a one-pass signature (the iOS app's
     * Sign output).
     */
    fun verify(armoredMessage: String, publicKeys: List<KeyRecord>): String {
        return if (armoredMessage.contains("-----BEGIN PGP SIGNED MESSAGE-----")) {
            verifyCleartext(armoredMessage, publicKeys)
        } else {
            verifySignedMessage(armoredMessage, publicKeys)
        }
    }

    /** Verifies an armored PGP MESSAGE containing one-pass signature + literal data. */
    private fun verifySignedMessage(armoredMessage: String, publicKeys: List<KeyRecord>): String {
        val decoded = PGPUtil.getDecoderStream(
            ByteArrayInputStream(armoredMessage.toByteArray(Charsets.UTF_8))
        )
        val result = readSignedLiteral(decoded, publicKeys)
        when (val s = result.signature) {
            is SignatureStatus.Valid -> return result.plaintext
            is SignatureStatus.UnknownKey ->
                throw PGPException("Signed by key ${s.keyId}, which is not in your keyring — import the sender's public key first")
            is SignatureStatus.Invalid ->
                throw PGPException("Signature verification failed: ${s.reason}")
            SignatureStatus.NotSigned ->
                throw PGPException("No signature found in message")
        }
    }

    private fun verifyCleartext(armoredMessage: String, publicKeys: List<KeyRecord>): String {
        val inputStream = ArmoredInputStream(
            ByteArrayInputStream(armoredMessage.toByteArray(Charsets.UTF_8))
        )

        // Read clear-text body
        val bodyOut = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (inputStream.isClearText) {
            val read = inputStream.read(buf)
            if (read < 0) break
            bodyOut.write(buf, 0, read)
        }
        val body = bodyOut.toByteArray()

        // Read objects once — a second nextObject() call after a failed cast would
        // consume and lose the signature list
        val pgpFactory = PGPObjectFactory(inputStream, JcaKeyFingerprintCalculator())
        var sigList: org.bouncycastle.openpgp.PGPSignatureList? = null
        var obj = pgpFactory.nextObject()
        while (obj != null && sigList == null) {
            when (obj) {
                is org.bouncycastle.openpgp.PGPSignatureList -> sigList = obj
                is PGPOnePassSignatureList -> {} // skip; the matching PGPSignatureList follows
            }
            if (sigList == null) obj = pgpFactory.nextObject()
        }
        val signatures = sigList ?: throw PGPException("No signature found in message")

        // Try each provided public key
        for (rec in publicKeys) {
            runCatching {
                val pubRing = loadPublicRing(rec.armoredPublic)
                for (i in 0 until signatures.size()) {
                    val sig = signatures[i]
                    val pubKey = pubRing.getPublicKey(sig.keyID) ?: continue
                    sig.init(
                        JcaPGPContentVerifierBuilderProvider().setProvider("BC"), pubKey
                    )
                    sig.update(body)
                    if (sig.verify()) return body.toString(Charsets.UTF_8).trim()
                }
            }
        }
        throw PGPException("Signature verification failed — no matching public key found")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun extractEncryptionKey(armoredPublic: String): PGPPublicKey? {
        val ring = loadPublicRing(armoredPublic)
        return ring.publicKeys.asSequence()
            .firstOrNull { it.isEncryptionKey }
            ?: ring.publicKey
    }

    private fun loadPublicRing(armored: String): PGPPublicKeyRing {
        val decoded = PGPUtil.getDecoderStream(
            ByteArrayInputStream(armored.toByteArray(Charsets.UTF_8))
        )
        val factory = PGPObjectFactory(decoded, JcaKeyFingerprintCalculator())
        return factory.nextObject() as? PGPPublicKeyRing
            ?: throw PGPException("Could not parse public key ring")
    }

    private fun loadSecretRing(armored: String): PGPSecretKeyRing {
        val decoded = PGPUtil.getDecoderStream(
            ByteArrayInputStream(armored.toByteArray(Charsets.UTF_8))
        )
        val factory = PGPObjectFactory(decoded, JcaKeyFingerprintCalculator())
        return factory.nextObject() as? PGPSecretKeyRing
            ?: throw PGPException("Could not parse private key ring")
    }

    /**
     * Reads a (possibly compressed) packet stream of the shape
     * [one-pass signature?] literal-data [signature?], collecting the literal
     * bytes and checking the signature against [verificationKeys] on the fly.
     */
    private fun readSignedLiteral(
        inputStream: java.io.InputStream,
        verificationKeys: List<KeyRecord>
    ): DecryptionResult {
        var onePass: PGPOnePassSignature? = null
        var signerRecord: KeyRecord? = null
        var literalBytes: ByteArray? = null
        var status: SignatureStatus = SignatureStatus.NotSigned

        fun walk(factory: PGPObjectFactory) {
            var obj = factory.nextObject()
            while (obj != null) {
                when (obj) {
                    is PGPCompressedData ->
                        walk(PGPObjectFactory(obj.dataStream, JcaKeyFingerprintCalculator()))
                    is PGPOnePassSignatureList -> if (obj.size() > 0) {
                        val ops = obj.get(0)
                        val (pubKey, rec) = findVerificationKey(ops.keyID, verificationKeys)
                        if (pubKey != null) {
                            ops.init(JcaPGPContentVerifierBuilderProvider().setProvider("BC"), pubKey)
                            onePass = ops
                            signerRecord = rec
                        } else {
                            status = SignatureStatus.UnknownKey(formatKeyId(ops.keyID))
                        }
                    }
                    is PGPLiteralData -> {
                        val buf = ByteArrayOutputStream()
                        val lit = obj.inputStream
                        val chunk = ByteArray(1 shl 13)
                        while (true) {
                            val n = lit.read(chunk)
                            if (n < 0) break
                            buf.write(chunk, 0, n)
                            onePass?.update(chunk, 0, n)
                        }
                        literalBytes = buf.toByteArray()
                    }
                    is PGPSignatureList -> {
                        val ops = onePass
                        if (ops != null && obj.size() > 0) {
                            status = try {
                                if (ops.verify(obj.get(0))) {
                                    SignatureStatus.Valid(signerRecord?.displayName, formatKeyId(ops.keyID))
                                } else {
                                    SignatureStatus.Invalid("signature does not match the message content")
                                }
                            } catch (e: PGPException) {
                                SignatureStatus.Invalid(e.message ?: "verification error")
                            }
                        }
                    }
                }
                obj = factory.nextObject()
            }
        }

        walk(PGPObjectFactory(inputStream, JcaKeyFingerprintCalculator()))
        val bytes = literalBytes ?: throw PGPException("No message content found")
        return DecryptionResult(bytes.toString(Charsets.UTF_8), status)
    }

    /** Finds the public key (and its record) matching a signature's key ID. */
    private fun findVerificationKey(
        keyId: Long,
        records: List<KeyRecord>
    ): Pair<PGPPublicKey?, KeyRecord?> {
        for (rec in records) {
            if (!rec.hasPublic) continue
            val key = runCatching { loadPublicRing(rec.armoredPublic).getPublicKey(keyId) }.getOrNull()
            if (key != null) return key to rec
        }
        return null to null
    }

    private fun formatKeyId(keyId: Long): String = "%016X".format(keyId)
}
