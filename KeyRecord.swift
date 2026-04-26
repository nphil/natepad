import Foundation

/// One stored key. Mirrors the web app's record shape.
/// `armoredPrivate` is the OpenPGP-encrypted private key block (passphrase-protected).
struct KeyRecord: Identifiable, Codable, Hashable {
    var id: String              // fingerprint, uppercase
    var fingerprint: String
    var userIDs: [String]       // ["Name <email>"]
    var hasPrivate: Bool
    var hasPublic: Bool
    var armoredPublic: String?
    var armoredPrivate: String?
    var createdAt: Date

    /// First user ID parsed into name + email.
    var primaryUser: (name: String, email: String) {
        guard let first = userIDs.first else { return ("(no user id)", "") }
        // Match "Name <email>"
        if let open = first.firstIndex(of: "<"), let close = first.firstIndex(of: ">"), open < close {
            let name = first[..<open].trimmingCharacters(in: .whitespaces)
            let email = first[first.index(after: open)..<close].trimmingCharacters(in: .whitespaces)
            return (String(name), String(email))
        }
        return (first, "")
    }

    /// Pretty-formatted fingerprint, e.g. "ABCD 1234 EFGH …"
    var prettyFingerprint: String {
        stride(from: 0, to: fingerprint.count, by: 4)
            .map { offset in
                let start = fingerprint.index(fingerprint.startIndex, offsetBy: offset)
                let end = fingerprint.index(start, offsetBy: 4, limitedBy: fingerprint.endIndex) ?? fingerprint.endIndex
                return String(fingerprint[start..<end])
            }
            .joined(separator: " ")
    }

    var shortID: String {
        String(fingerprint.suffix(8))
    }

    /// "Nitin <nitin@example.com> · 12345678"
    var label: String {
        let (name, email) = primaryUser
        let head = name.isEmpty ? email : (email.isEmpty ? name : "\(name) <\(email)>")
        return "\(head) · \(shortID)"
    }
}
