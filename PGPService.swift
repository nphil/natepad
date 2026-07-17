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
        case passphraseRequired

        var errorDescription: String? {
            switch self {
            case .noMatchingDecryptionKey: return "No private key in your keychain matches this message."
            case .verificationFailed(let why): return "Signature verification failed: \(why)"
            case .parseFailed(let why): return "Could not parse: \(why)"
            case .wrongPassphrase: return "Wrong passphrase."
            case .passphraseRequired: return "This key is protected by a passphrase."
            }
        }
    }

    /// What we learned about an embedded signature while decrypting.
    enum SignatureStatus: Equatable {
        case notSigned
        case valid
        case invalid(String)
    }

    struct DecryptionResult {
        let plaintext: String
        let signature: SignatureStatus
    }

    // Raw values of ObjectivePGP's PGPErrorCode (PGPTypes.h). The library reports
    // signature state through these codes, so match on numbers, not strings.
    private enum PGPErrorCode {
        static let passphraseRequired = 5
        static let passphraseInvalid = 6
        static let invalidSignature = 7
        static let notSigned = 8
        static let missingSignature = 10
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

    /// Decrypts and, when the message carries a signature, verifies it against the
    /// provided public keys. Uses `decrypt:verified:...` because the older
    /// `andVerifySignature:` variant returns plaintext even when verification fails,
    /// which Swift's error bridging then silently swallows.
    static func decrypt(armoredMessage: String,
                        using privateKeys: [KeyRecord],
                        verificationKeys: [KeyRecord],
                        passphraseProvider: @escaping (KeyRecord) -> String?) throws -> DecryptionResult {
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

        var verifiedCode: Int32 = 0
        var decryptionError: NSError?
        let decrypted: Data
        do {
            decrypted = try ObjectivePGP.decrypt(
                data,
                verified: &verifiedCode,
                certifyWithRootKey: false,
                using: allKeys,
                passphraseForKey: { optKey in
                    guard let key = optKey else { return nil }
                    let id = key.keyID.longIdentifier.uppercased()
                    // Direct match on primary key ID
                    if let rec = keyIDToRecord[id] { return passphraseProvider(rec) }
                    // Subkey fallback: ObjectivePGP may pass a subkey whose ID differs from the
                    // primary key we stored. Try every private record's passphrase.
                    return privateKeys.compactMap { passphraseProvider($0) }.first
                },
                decryptionError: &decryptionError
            )
        } catch {
            // On decryption failure the real reason lands in decryptionError; the
            // thrown error is just Swift's bridging of the (empty) verification slot.
            throw mapDecryptionError(decryptionError ?? (error as NSError))
        }

        let plaintext = String(data: decrypted, encoding: .utf8) ?? ""
        let status: SignatureStatus
        switch Int(verifiedCode) {
        case 0:
            status = .valid
        case PGPErrorCode.notSigned:
            status = .notSigned
        case PGPErrorCode.invalidSignature:
            status = .invalid("The signature is bad, or the signer's public key is not in your keyring.")
        case PGPErrorCode.missingSignature:
            status = .invalid("The message's signature is incomplete or malformed.")
        default:
            status = .invalid("Signature check failed (code \(verifiedCode)).")
        }
        return DecryptionResult(plaintext: plaintext, signature: status)
    }

    private static func mapDecryptionError(_ error: NSError) -> Error {
        switch error.code {
        case PGPErrorCode.passphraseRequired: return PGPError.passphraseRequired
        case PGPErrorCode.passphraseInvalid: return PGPError.wrongPassphrase
        default: break
        }
        let desc = error.localizedDescription.lowercased()
        if desc.contains("missing private key") || desc.contains("nothing to decrypt") {
            return PGPError.noMatchingDecryptionKey
        }
        return error
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
    /// Handles both armored PGP MESSAGE blocks (one-pass signature + literal data,
    /// what this app's Sign produces) and cleartext SIGNED MESSAGE blocks (what
    /// GnuPG's --clearsign and the Android app produce).
    static func verify(armoredMessage: String, using publicKeys: [KeyRecord]) throws -> String {
        if armoredMessage.contains("-----BEGIN PGP SIGNED MESSAGE-----") {
            return try verifyCleartext(armoredMessage: armoredMessage, using: publicKeys)
        }
        guard let data = armoredMessage.data(using: .utf8) else {
            throw PGPError.parseFailed("input is not UTF-8")
        }
        let keys = readPublicKeys(from: publicKeys)
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

    /// Verifies an RFC 4880 §7 cleartext signed message. ObjectivePGP has no native
    /// cleartext support, so parse the framework here and check the embedded
    /// signature as a detached signature over the canonicalised text.
    private static func verifyCleartext(armoredMessage: String, using publicKeys: [KeyRecord]) throws -> String {
        let lines = armoredMessage
            .replacingOccurrences(of: "\r\n", with: "\n")
            .components(separatedBy: "\n")
        guard let headerIdx = lines.firstIndex(where: { $0.hasPrefix("-----BEGIN PGP SIGNED MESSAGE-----") }) else {
            throw PGPError.parseFailed("not a cleartext signed message")
        }
        // Skip armor headers ("Hash: SHA256") up to and including the blank separator line.
        var i = headerIdx + 1
        while i < lines.count, !lines[i].isEmpty { i += 1 }
        i += 1

        var bodyLines: [String] = []
        while i < lines.count, !lines[i].hasPrefix("-----BEGIN PGP SIGNATURE-----") {
            let line = lines[i]
            // Dash-unescape ("- -----foo" was escaped by the signer).
            bodyLines.append(line.hasPrefix("- ") ? String(line.dropFirst(2)) : line)
            i += 1
        }
        guard i < lines.count else {
            throw PGPError.parseFailed("cleartext message has no signature block")
        }
        // The newline immediately before the signature block belongs to the armor,
        // not the signed text.
        if bodyLines.last?.isEmpty == true { bodyLines.removeLast() }

        guard let sigData = lines[i...].joined(separator: "\n").data(using: .utf8) else {
            throw PGPError.parseFailed("bad signature block")
        }
        let keys = readPublicKeys(from: publicKeys)

        // The signature covers CRLF-canonicalised text. Strict RFC form also strips
        // trailing whitespace from each line; some implementations sign it raw, and
        // a trailing newline on the last line is ambiguous. Try the variants.
        let stripped = bodyLines.map { line -> String in
            var l = line
            while l.hasSuffix(" ") || l.hasSuffix("\t") { l.removeLast() }
            return l
        }
        var candidates: [String] = []
        for joined in [stripped.joined(separator: "\r\n"), bodyLines.joined(separator: "\r\n")] {
            candidates.append(joined)
            candidates.append(joined + "\r\n")
        }
        var seen = Set<String>()
        var lastError: Error? = nil
        for candidate in candidates where seen.insert(candidate).inserted {
            guard let candData = candidate.data(using: .utf8) else { continue }
            do {
                try ObjectivePGP.verify(candData, withSignature: sigData, using: keys, passphraseForKey: nil)
                return bodyLines.joined(separator: "\n")
            } catch {
                lastError = error
            }
        }
        throw PGPError.verificationFailed(lastError?.localizedDescription ?? "signature does not match the message text")
    }

    // MARK: - Helpers

    private static func readPublicKeys(from records: [KeyRecord]) -> [Key] {
        records.compactMap { rec in
            guard let armored = rec.armoredPublic,
                  let data = try? armoredData(armored) else { return nil }
            return try? ObjectivePGP.readKeys(from: data).first
        }
    }

    private static func armoredData(_ armored: String?) throws -> Data {
        guard let s = armored, let d = s.data(using: .utf8) else {
            throw PGPError.parseFailed("missing armored data")
        }
        return d
    }
}
