import Foundation
import ObjectivePGP

/// Thin wrapper around ObjectivePGP. All methods throw on failure.
/// Operations match the web app's modes: encrypt, decrypt, sign, verify, generate, parse.
enum PGPService {

    enum PGPError: LocalizedError {
        case noMatchingDecryptionKey
        case verificationFailed(String)
        case parseFailed(String)
        case wrongPassphrase

        var errorDescription: String? {
            switch self {
            case .noMatchingDecryptionKey: return "No private key in your keychain matches this message."
            case .verificationFailed(let why): return "Signature verification failed: \(why)"
            case .parseFailed(let why): return "Could not parse: \(why)"
            case .wrongPassphrase: return "Wrong passphrase."
            }
        }
    }

    // MARK: - Parsing

    /// Reads one or more armored or binary key blocks from text. Returns parsed records.
    static func parseKeys(from text: String) throws -> [KeyRecord] {
        guard let data = text.data(using: .utf8) else {
            throw PGPError.parseFailed("input is not UTF-8")
        }
        let keys: [Key]
        do {
            keys = try ObjectivePGP.readKeys(from: data)
        } catch {
            throw PGPError.parseFailed(error.localizedDescription)
        }
        return try keys.map { try record(from: $0) }
    }

    /// Convert an ObjectivePGP `Key` into our serializable record.
    private static func record(from key: Key) throws -> KeyRecord {
        let fp = key.keyID.longIdentifier.uppercased()
        // Prefer the full fingerprint if available
        let fingerprint: String = {
            if let pk = key.publicKey {
                return pk.fingerprint.description.replacingOccurrences(of: " ", with: "").uppercased()
            }
            if let sk = key.secretKey {
                return sk.fingerprint.description.replacingOccurrences(of: " ", with: "").uppercased()
            }
            return fp
        }()

        let userIDs: [String] = {
            if let pk = key.publicKey {
                return pk.users.compactMap { $0.userID }
            }
            if let sk = key.secretKey {
                return sk.users.compactMap { $0.userID }
            }
            return []
        }()

        // Export armored representations
        var pubArmored: String?
        var secArmored: String?
        if let pubData = try? key.export(keyType: .public) {
            pubArmored = Armor.armored(pubData, as: .publicKey)
        }
        if key.isSecret, let secData = try? key.export(keyType: .secret) {
            secArmored = Armor.armored(secData, as: .secretKey)
        }

        return KeyRecord(
            id: fingerprint,
            fingerprint: fingerprint,
            userIDs: userIDs,
            hasPrivate: secArmored != nil,
            hasPublic: pubArmored != nil,
            armoredPublic: pubArmored,
            armoredPrivate: secArmored,
            createdAt: Date()
        )
    }

    // MARK: - Generation

    /// Generates a new RSA-4096 key pair. ObjectivePGP's ECC support is limited, so we stick with RSA.
    static func generate(name: String, email: String, passphrase: String) throws -> KeyRecord {
        let userID: String = {
            if !name.isEmpty && !email.isEmpty { return "\(name) <\(email)>" }
            return name.isEmpty ? email : name
        }()
        let generator = KeyGenerator()
        generator.keyBitsLength = 4096
        let key = generator.generate(for: userID, passphrase: passphrase)
        return try record(from: key)
    }

    // MARK: - Encrypt / Decrypt / Sign / Verify

    /// Encrypts plaintext to one or more recipients, optionally signing with a private key.
    static func encrypt(plaintext: String,
                        to recipients: [KeyRecord],
                        signWith signer: KeyRecord? = nil,
                        passphrase: String? = nil) throws -> String {
        guard let data = plaintext.data(using: .utf8) else {
            throw PGPError.parseFailed("plaintext is not UTF-8")
        }
        var keys: [Key] = try recipients.compactMap { rec in
            try ObjectivePGP.readKeys(from: armoredData(rec.armoredPublic)).first
        }
        if let signer = signer, let armored = signer.armoredPrivate {
            if let key = try ObjectivePGP.readKeys(from: armoredData(armored)).first {
                keys.append(key)
            }
        }
        do {
            let encrypted = try ObjectivePGP.encrypt(
                data,
                addSignature: signer != nil,
                using: keys,
                passphraseForKey: { _ in passphrase }
            )
            return Armor.armored(encrypted, as: .message)
        } catch {
            // ObjectivePGP throws an opaque error on bad passphrase; map common cases.
            let desc = error.localizedDescription.lowercased()
            if desc.contains("passphrase") { throw PGPError.wrongPassphrase }
            throw error
        }
    }

    /// Returns (plaintext, signerFingerprintIfVerified).
    static func decrypt(armoredMessage: String,
                        using privateKeys: [KeyRecord],
                        verificationKeys: [KeyRecord],
                        passphraseProvider: @escaping (KeyRecord) -> String?) throws -> (String, String?) {
        guard let data = armoredMessage.data(using: .utf8) else {
            throw PGPError.parseFailed("input is not UTF-8")
        }

        // Build the key list: all our private keys (for decryption) + all public keys (for verify).
        var allKeys: [Key] = []
        var keyIDToRecord: [String: KeyRecord] = [:]
        for rec in privateKeys {
            if let armored = rec.armoredPrivate,
               let key = try? ObjectivePGP.readKeys(from: armoredData(armored)).first {
                allKeys.append(key)
                keyIDToRecord[key.keyID.longIdentifier.uppercased()] = rec
            }
        }
        for rec in verificationKeys {
            if let armored = rec.armoredPublic,
               let key = try? ObjectivePGP.readKeys(from: armoredData(armored)).first {
                allKeys.append(key)
            }
        }

        do {
            let decrypted = try ObjectivePGP.decrypt(
                data,
                andVerifySignature: true,
                using: allKeys,
                passphraseForKey: { key in
                    let id = key.keyID.longIdentifier.uppercased()
                    if let rec = keyIDToRecord[id] {
                        return passphraseProvider(rec)
                    }
                    return nil
                }
            )
            let plaintext = String(data: decrypted, encoding: .utf8) ?? ""
            return (plaintext, nil) // ObjectivePGP doesn't easily expose the verified signer; return nil for now
        } catch {
            let desc = error.localizedDescription.lowercased()
            if desc.contains("passphrase") { throw PGPError.wrongPassphrase }
            if desc.contains("decrypt") { throw PGPError.noMatchingDecryptionKey }
            throw error
        }
    }

    /// Produces an armored signed message (PGP MESSAGE block containing literal data + signature).
    /// Verifiable in any standard PGP tool.
    static func sign(plaintext: String,
                     with signer: KeyRecord,
                     passphrase: String?) throws -> String {
        guard let data = plaintext.data(using: .utf8),
              let armored = signer.armoredPrivate else {
            throw PGPError.parseFailed("missing input or private key")
        }
        guard let key = try ObjectivePGP.readKeys(from: armoredData(armored)).first else {
            throw PGPError.parseFailed("could not read signing key")
        }
        do {
            let signed = try ObjectivePGP.sign(data, detached: false, using: [key], passphraseForKey: { _ in passphrase })
            return Armor.armored(signed, as: .message)
        } catch {
            let desc = error.localizedDescription.lowercased()
            if desc.contains("passphrase") { throw PGPError.wrongPassphrase }
            throw error
        }
    }

    /// Verifies a signed message. Returns the plaintext on success.
    static func verify(armoredMessage: String, using publicKeys: [KeyRecord]) throws -> String {
        guard let data = armoredMessage.data(using: .utf8) else {
            throw PGPError.parseFailed("input is not UTF-8")
        }
        var keys: [Key] = []
        for rec in publicKeys {
            if let armored = rec.armoredPublic,
               let key = try? ObjectivePGP.readKeys(from: armoredData(armored)).first {
                keys.append(key)
            }
        }
        // ObjectivePGP's verify works on the literal data inside the message.
        do {
            try ObjectivePGP.verify(data, withSignature: nil, using: keys, passphraseForKey: nil)
        } catch {
            throw PGPError.verificationFailed(error.localizedDescription)
        }
        // Decrypt the literal data wrapper to get the plaintext (works because the signed message has no encryption).
        let decrypted = (try? ObjectivePGP.decrypt(data, andVerifySignature: false, using: keys, passphraseForKey: nil)) ?? Data()
        return String(data: decrypted, encoding: .utf8) ?? ""
    }

    // MARK: - Helpers

    private static func armoredData(_ armored: String?) throws -> Data {
        guard let s = armored, let d = s.data(using: .utf8) else {
            throw PGPError.parseFailed("missing armored data")
        }
        return d
    }
}
