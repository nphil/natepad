import SwiftUI
import CryptoKit
import CommonCrypto

// MARK: - Backup container service
//
// Password-encrypted backup of all keys. The container format is shared with the
// Android app so a backup made on either platform restores on the other:
//   JSON { format, version, kdf, iterations, salt, nonce, ciphertext }
//   - KDF:       PBKDF2-HMAC-SHA256, 600 000 iterations, 32-byte key
//   - Cipher:    AES-256-GCM, 12-byte nonce, 16-byte tag appended to ciphertext
//                (CryptoKit ciphertext+tag matches javax.crypto GCM output)
//   - Plaintext: JSON { keys: [{fingerprint, userIds, armoredPublic?, armoredPrivate?}] }

enum BackupError: LocalizedError {
    case notABackup
    case unsupportedVersion(Int)
    case wrongPassword
    case keyDerivationFailed

    var errorDescription: String? {
        switch self {
        case .notABackup: return "Not a NatePad backup file."
        case .unsupportedVersion(let v): return "Unsupported backup version \(v)."
        case .wrongPassword: return "Wrong password or corrupted backup."
        case .keyDerivationFailed: return "Could not derive the encryption key."
        }
    }
}

enum BackupService {
    static let fileExtension = "natepadbackup"

    private static let format = "natepad-backup"
    private static let version = 1
    private static let kdf = "pbkdf2-hmac-sha256"
    private static let iterations = 600_000
    private static let saltBytes = 16
    private static let tagBytes = 16

    private struct Envelope: Codable {
        let format: String
        let version: Int
        let kdf: String
        let iterations: Int
        let salt: String
        let nonce: String
        let ciphertext: String
    }

    private struct BackupKey: Codable {
        let fingerprint: String
        let userIds: [String]
        let armoredPublic: String?
        let armoredPrivate: String?
    }

    private struct Payload: Codable {
        let keys: [BackupKey]
    }

    /// Encrypts all keys into a portable backup container.
    static func createBackup(keys: [KeyRecord], password: String) throws -> Data {
        let payload = Payload(keys: keys.map { rec in
            BackupKey(
                fingerprint: rec.fingerprint,
                userIds: rec.userIDs,
                armoredPublic: rec.armoredPublic?.isEmpty == false ? rec.armoredPublic : nil,
                armoredPrivate: rec.armoredPrivate?.isEmpty == false ? rec.armoredPrivate : nil
            )
        })
        let plaintext = try JSONEncoder().encode(payload)

        var salt = Data(count: saltBytes)
        let saltResult = salt.withUnsafeMutableBytes { ptr in
            SecRandomCopyBytes(kSecRandomDefault, saltBytes, ptr.baseAddress!)
        }
        guard saltResult == errSecSuccess else { throw BackupError.keyDerivationFailed }

        let key = try deriveKey(password: password, salt: salt, iterations: iterations)
        let nonce = AES.GCM.Nonce()
        let sealed = try AES.GCM.seal(plaintext, using: key, nonce: nonce)
        // Android's javax.crypto GCM output is ciphertext||tag — match it.
        let ciphertext = sealed.ciphertext + sealed.tag

        let envelope = Envelope(
            format: format,
            version: version,
            kdf: kdf,
            iterations: iterations,
            salt: salt.base64EncodedString(),
            nonce: Data(nonce).base64EncodedString(),
            ciphertext: ciphertext.base64EncodedString()
        )
        return try JSONEncoder().encode(envelope)
    }

    /// Decrypts a backup container and returns the contained keys.
    static func restoreBackup(_ data: Data, password: String) throws -> [KeyRecord] {
        guard let envelope = try? JSONDecoder().decode(Envelope.self, from: data),
              envelope.format == format else {
            throw BackupError.notABackup
        }
        guard envelope.version == version else {
            throw BackupError.unsupportedVersion(envelope.version)
        }
        guard envelope.kdf == kdf,
              let salt = Data(base64Encoded: envelope.salt),
              let nonceData = Data(base64Encoded: envelope.nonce),
              let combined = Data(base64Encoded: envelope.ciphertext),
              combined.count > tagBytes else {
            throw BackupError.notABackup
        }

        let key = try deriveKey(password: password, salt: salt, iterations: envelope.iterations)
        let ciphertext = combined.prefix(combined.count - tagBytes)
        let tag = combined.suffix(tagBytes)

        let plaintext: Data
        do {
            let box = try AES.GCM.SealedBox(
                nonce: AES.GCM.Nonce(data: nonceData),
                ciphertext: ciphertext,
                tag: tag
            )
            plaintext = try AES.GCM.open(box, using: key)
        } catch {
            throw BackupError.wrongPassword
        }

        let payload = try JSONDecoder().decode(Payload.self, from: plaintext)
        return payload.keys.map { k in
            let fp = k.fingerprint.uppercased()
            return KeyRecord(
                id: fp,
                fingerprint: fp,
                userIDs: k.userIds,
                hasPrivate: !(k.armoredPrivate ?? "").isEmpty,
                hasPublic: !(k.armoredPublic ?? "").isEmpty,
                armoredPublic: k.armoredPublic,
                armoredPrivate: k.armoredPrivate,
                createdAt: Date()
            )
        }
    }

    private static func deriveKey(password: String, salt: Data, iterations: Int) throws -> SymmetricKey {
        var derived = Data(count: 32)
        let pwBytes = Array(password.utf8)
        let status = derived.withUnsafeMutableBytes { derivedPtr in
            salt.withUnsafeBytes { saltPtr in
                CCKeyDerivationPBKDF(
                    CCPBKDFAlgorithm(kCCPBKDF2),
                    pwBytes.map { Int8(bitPattern: $0) }, pwBytes.count,
                    saltPtr.baseAddress?.assumingMemoryBound(to: UInt8.self), salt.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                    UInt32(iterations),
                    derivedPtr.baseAddress?.assumingMemoryBound(to: UInt8.self), 32
                )
            }
        }
        guard status == kCCSuccess else { throw BackupError.keyDerivationFailed }
        return SymmetricKey(data: derived)
    }
}

// MARK: - Export sheet

struct BackupExportSheet: View {
    @EnvironmentObject var store: KeyStore
    @Environment(\.dismiss) var dismiss

    @State private var password = ""
    @State private var password2 = ""
    @State private var isWorking = false
    @State private var error: String?
    @State private var done = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Backup password") {
                    SecureField("Choose a backup password", text: $password)
                    SecureField("Confirm password", text: $password2)
                    Text("You will need this password to restore. It cannot be recovered if lost.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                if let error {
                    Section { Text(error).foregroundStyle(.red) }
                }
                if done {
                    Section { Label("Backup shared.", systemImage: "checkmark.circle.fill").foregroundStyle(.green) }
                }
                Section {
                    Text("Backs up all \(store.keys.count) key(s) into a single AES-256-GCM encrypted file. The same file restores on iOS and Android.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Export backup")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await export() }
                    } label: {
                        if isWorking { ProgressView() } else { Text("Create & Share") }
                    }
                    .disabled(isWorking || !valid)
                }
            }
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Done") {
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                    }
                }
            }
            .interactiveDismissDisabled(isWorking)
            .presentationDetents([.medium, .large])
            .presentationBackground(.regularMaterial)
        }
    }

    private var valid: Bool {
        password.count >= 8 && password == password2
    }

    private func export() async {
        isWorking = true
        defer { isWorking = false }
        error = nil
        let keys = store.keys  // capture on main actor before hopping off
        do {
            let data = try await Task.detached(priority: .userInitiated) {
                try BackupService.createBackup(keys: keys, password: password)
            }.value

            let df = DateFormatter()
            df.dateFormat = "yyyyMMdd-HHmm"
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("natepad-backup-\(df.string(from: Date())).\(BackupService.fileExtension)")
            try data.write(to: url, options: .atomic)

            guard let presenter = topMostViewController() else {
                error = "Could not find a window to present the share sheet from."
                return
            }
            let activity = UIActivityViewController(activityItems: [url], applicationActivities: nil)
            // iPad requires a popover anchor
            activity.popoverPresentationController?.sourceView = presenter.view
            activity.popoverPresentationController?.sourceRect = CGRect(
                x: presenter.view.bounds.midX, y: presenter.view.bounds.midY, width: 0, height: 0
            )
            presenter.present(activity, animated: true)
            done = true
        } catch {
            self.error = error.localizedDescription
        }
    }
}

// MARK: - Import sheet

struct BackupImportSheet: View {
    @EnvironmentObject var store: KeyStore
    @Environment(\.dismiss) var dismiss

    @State private var fileData: Data?
    @State private var fileName: String?
    @State private var password = ""
    @State private var isWorking = false
    @State private var resultMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Button {
                        presentPicker()
                    } label: {
                        Label(fileName ?? "Choose backup file…", systemImage: "doc.badge.plus")
                    }
                    Text("Select a .\(BackupService.fileExtension) file exported from NatePad on iOS or Android.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                if fileData != nil {
                    Section("Backup password") {
                        SecureField("Password", text: $password)
                            .submitLabel(.go)
                            .onSubmit { Task { await restore() } }
                    }
                }
                if let resultMessage {
                    Section { Text(resultMessage) }
                }
            }
            .navigationTitle("Import backup")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await restore() }
                    } label: {
                        if isWorking { ProgressView() } else { Text("Restore") }
                    }
                    .disabled(isWorking || fileData == nil || password.isEmpty)
                }
            }
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Done") {
                        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                    }
                }
            }
            .presentationDetents([.medium, .large])
            .presentationBackground(.regularMaterial)
        }
    }

    private func presentPicker() {
        guard let presenter = topMostViewController() else {
            resultMessage = "Could not find a window to present the file picker from."
            return
        }
        // [.item] + asCopy:true — see PickerDelegate docs in Sheets.swift
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.item], asCopy: true)
        picker.allowsMultipleSelection = false
        picker.shouldShowFileExtensions = true
        _ = PickerDelegate(picker: picker, onPicked: { urls in
            guard let url = urls.first else { return }
            let accessed = url.startAccessingSecurityScopedResource()
            defer { if accessed { url.stopAccessingSecurityScopedResource() } }
            do {
                fileData = try Data(contentsOf: url)
                fileName = url.lastPathComponent
                resultMessage = nil
            } catch {
                resultMessage = "Could not read file: \(error.localizedDescription)"
            }
        })
        presenter.present(picker, animated: true)
    }

    private func restore() async {
        guard let data = fileData, !password.isEmpty else { return }
        isWorking = true
        defer { isWorking = false }
        do {
            let pw = password
            let restored = try await Task.detached(priority: .userInitiated) {
                try BackupService.restoreBackup(data, password: pw)
            }.value
            for rec in restored {
                store.add(rec)
            }
            resultMessage = "Restored \(restored.count) key\(restored.count == 1 ? "" : "s")."
            try? await Task.sleep(nanoseconds: 600_000_000)
            dismiss()
        } catch {
            resultMessage = error.localizedDescription
        }
    }
}
