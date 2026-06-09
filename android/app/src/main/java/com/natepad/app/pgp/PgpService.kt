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
import org.bouncycastle.openpgp.PGPOnePassSignatureList
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureGenerator
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
        val pubRing = parsed.publicRing ?: parsed.secretRing?.publicKeys?.asSequence()?.firstOrNull()
            ?.let { return@let null } // will extract from secret ring below
        val secretRing = parsed.secretRing

        // Extract public ring from secret ring if no separate public ring
        val effectivePubRing: PGPPublicKeyRing? = parsed.publicRing
            ?: secretRing?.let { sr ->
                val pubKeys = sr.secretKeys.asSequence().map { it.publicKey }.toList()
                if (pubKeys.isEmpty()) return null
                PGPPublicKeyRing(pubKeys)
            }

        if (effectivePubRing == null) return null

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

    fun decrypt(
        armoredMessage: String,
        keys: List<KeyRecord>,
        passphraseProvider: (KeyRecord) -> String?
    ): String {
        val decoded = PGPUtil.getDecoderStream(
            ByteArrayInputStream(armoredMessage.toByteArray(Charsets.UTF_8))
        )
        val pgpFactory = PGPObjectFactory(decoded, JcaKeyFingerprintCalculator())
        val encDataList = when (val o = pgpFactory.nextObject()) {
            is PGPEncryptedDataList -> o
            else -> pgpFactory.nextObject() as? PGPEncryptedDataList
                ?: throw PGPException("No encrypted data found")
        }

        var decryptedStream: ByteArrayOutputStream? = null

        for (encData in encDataList.encryptedDataObjects) {
            if (encData !is PGPPublicKeyEncryptedData) continue
            val keyId = encData.keyID
            val matchingKey = keys.firstOrNull { rec ->
                rec.hasPrivate && loadSecretRing(rec.armoredPrivate).secretKeys.asSequence()
                    .any { it.keyID == keyId }
            } ?: continue

            val passphrase = passphraseProvider(matchingKey) ?: continue
            val secretRing = loadSecretRing(matchingKey.armoredPrivate)
            val secretKey = secretRing.secretKeys.asSequence().first { it.keyID == keyId }
            val privKey = secretKey.extractPrivateKey(
                JcePBESecretKeyDecryptorBuilder().setProvider("BC").build(passphrase.toCharArray())
            )

            val clearStream = encData.getDataStream(
                JcePublicKeyDataDecryptorFactoryBuilder().setProvider("BC").build(privKey)
            )
            decryptedStream = ByteArrayOutputStream()
            extractLiteralData(clearStream, decryptedStream)
            break
        }

        return decryptedStream?.toString(Charsets.UTF_8)
            ?: throw PGPException("Could not decrypt — wrong key or bad passphrase")
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

    fun verify(armoredMessage: String, publicKeys: List<KeyRecord>): String {
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

        val pgpFactory = PGPObjectFactory(inputStream, JcaKeyFingerprintCalculator())
        val sigList = pgpFactory.nextObject() as? PGPOnePassSignatureList
            ?: pgpFactory.nextObject() as? org.bouncycastle.openpgp.PGPSignatureList
            ?: throw PGPException("No signature found in message")

        // Try each provided public key
        for (rec in publicKeys) {
            runCatching {
                val pubRing = loadPublicRing(rec.armoredPublic)
                when (sigList) {
                    is org.bouncycastle.openpgp.PGPSignatureList -> {
                        for (i in 0 until sigList.size()) {
                            val sig = sigList[i]
                            val pubKey = pubRing.getPublicKey(sig.keyID) ?: continue
                            sig.init(
                                JcaPGPContentVerifierBuilderProvider().setProvider("BC"), pubKey
                            )
                            sig.update(body)
                            if (sig.verify()) return body.toString(Charsets.UTF_8).trim()
                        }
                    }
                    else -> {}
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

    private fun extractLiteralData(inputStream: java.io.InputStream, out: ByteArrayOutputStream) {
        val factory = PGPObjectFactory(inputStream, JcaKeyFingerprintCalculator())
        var obj = factory.nextObject()
        while (obj != null) {
            when (obj) {
                is PGPCompressedData -> {
                    extractLiteralData(obj.dataStream, out)
                    return
                }
                is PGPLiteralData -> {
                    obj.inputStream.copyTo(out)
                    return
                }
            }
            obj = factory.nextObject()
        }
    }
}
