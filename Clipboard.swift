import Foundation
import UIKit
import UniformTypeIdentifiers

// MARK: - PGP content detection

/// What kind of PGP block a piece of text contains.
enum PGPContentKind {
    case encryptedMessage   // -----BEGIN PGP MESSAGE-----
    case signedMessage      // -----BEGIN PGP SIGNED MESSAGE-----
    case publicKey          // -----BEGIN PGP PUBLIC KEY BLOCK-----
    case privateKey         // -----BEGIN PGP PRIVATE/SECRET KEY BLOCK-----

    /// Order matters: a SIGNED MESSAGE block also contains "BEGIN PGP MESSAGE"
    /// lookalikes, and key blocks may be pasted together with messages.
    static func detect(in text: String) -> PGPContentKind? {
        if text.contains("-----BEGIN PGP SIGNED MESSAGE-----") { return .signedMessage }
        if text.contains("-----BEGIN PGP MESSAGE-----") { return .encryptedMessage }
        if text.contains("-----BEGIN PGP PRIVATE KEY BLOCK-----") ||
           text.contains("-----BEGIN PGP SECRET KEY BLOCK-----") { return .privateKey }
        if text.contains("-----BEGIN PGP PUBLIC KEY BLOCK-----") { return .publicKey }
        return nil
    }
}

// MARK: - Sensitive copy

enum SensitivePasteboard {
    static let clearAfterSeconds: TimeInterval = 60

    /// Copies text that iOS wipes from the pasteboard after `clearAfterSeconds`,
    /// and never syncs to other devices via Universal Clipboard.
    static func copy(_ text: String) {
        UIPasteboard.general.setItems(
            [[UTType.utf8PlainText.identifier: text]],
            options: [
                .expirationDate: Date().addingTimeInterval(clearAfterSeconds),
                .localOnly: true,
            ]
        )
    }
}
