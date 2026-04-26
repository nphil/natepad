import SwiftUI
import UIKit
import UniformTypeIdentifiers

// MARK: - Generate

struct GenerateKeySheet: View {
    @EnvironmentObject var store: KeyStore
    @Environment(\.dismiss) var dismiss

    @State private var name = ""
    @State private var email = ""
    @State private var passphrase = ""
    @State private var passphrase2 = ""
    @State private var isWorking = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Identity") {
                    TextField("Name", text: $name)
                        .textContentType(.name)
                    TextField("Email", text: $email)
                        .keyboardType(.emailAddress)
                        .textContentType(.emailAddress)
                        .autocapitalization(.none)
                }
                Section("Passphrase") {
                    SecureField("Choose a strong passphrase", text: $passphrase)
                    SecureField("Confirm passphrase", text: $passphrase2)
                    Text("Protects your private key. Cannot be recovered if lost.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                if let error {
                    Section { Text(error).foregroundStyle(.red) }
                }
                Section {
                    Text("Generates an RSA-4096 key pair. Generation may take 30–60 seconds on iPhone.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Generate key")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await generate() }
                    } label: {
                        if isWorking { ProgressView() } else { Text("Generate") }
                    }
                    .disabled(isWorking || !valid)
                }
            }
            .interactiveDismissDisabled(isWorking)
            .presentationDetents([.medium, .large])
            .presentationBackground(.regularMaterial)
        }
    }

    private var valid: Bool {
        (!name.isEmpty || !email.isEmpty) && !passphrase.isEmpty && passphrase == passphrase2
    }

    private func generate() async {
        guard valid else { return }
        isWorking = true
        defer { isWorking = false }
        do {
            let rec = try await Task.detached(priority: .userInitiated) {
                try PGPService.generate(name: name, email: email, passphrase: passphrase)
            }.value
            store.add(rec)
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
    }
}

// MARK: - Document picker bridge
//
// Why this approach (after two failed attempts):
//   • SwiftUI .fileImporter drops its callback when the modifier sits inside a sheet.
//   • Wrapping UIDocumentPickerViewController as a SwiftUI sheet lets the coordinator
//     deallocate (its .delegate is weak) before the user taps "Open".
//   • Wrapping it as a UIViewControllerRepresentable in .background creates a UIViewController
//     that isn't reliably attached to the view-controller hierarchy — present(_:animated:)
//     can no-op silently.
//
// The bulletproof fix: walk the active window scene to the top-most presented view
// controller and present the picker directly from there with UIKit's own API. The delegate
// retains itself in init and releases that retain in its own callback methods, so it is
// guaranteed to be alive between "Open" tap and callback fire.

private final class PickerDelegate: NSObject, UIDocumentPickerDelegate {
    private var strongSelf: PickerDelegate?
    private let onPicked: ([URL]) -> Void
    private let onCancel: () -> Void

    init(picker: UIDocumentPickerViewController,
         onPicked: @escaping ([URL]) -> Void,
         onCancel: @escaping () -> Void = {}) {
        self.onPicked = onPicked
        self.onCancel = onCancel
        super.init()
        picker.delegate = self
        self.strongSelf = self  // self-retain until a delegate method fires
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        onPicked(urls)
        strongSelf = nil
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        onCancel()
        strongSelf = nil
    }
}

private func topMostViewController() -> UIViewController? {
    let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
    let scene = scenes.first(where: { $0.activationState == .foregroundActive }) ?? scenes.first
    let window = scene?.windows.first(where: \.isKeyWindow) ?? scene?.windows.first
    var top = window?.rootViewController
    while let presented = top?.presentedViewController {
        top = presented
    }
    return top
}

// MARK: - Import

struct ImportKeySheet: View {
    @EnvironmentObject var store: KeyStore
    @Environment(\.dismiss) var dismiss

    @State private var pastedText = ""
    @State private var resultMessage: String?
    @State private var isWorking = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Button {
                        presentPicker()
                    } label: {
                        Label("Choose file(s)…", systemImage: "doc.badge.plus")
                    }
                    Text("Accepts any file — .asc, .txt, .key, .gpg, .pgp")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Section("Or paste an armored block") {
                    TextEditor(text: $pastedText)
                        .frame(minHeight: 200)
                        .font(.system(.callout, design: .monospaced))
                }
                if let resultMessage {
                    Section { Text(resultMessage) }
                }
            }
            .navigationTitle("Import key")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await runImport() }
                    } label: {
                        if isWorking { ProgressView() } else { Text("Import") }
                    }
                    .disabled(isWorking || pastedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .presentationDetents([.large])
            .presentationBackground(.regularMaterial)
        }
    }

    private func presentPicker() {
        guard let presenter = topMostViewController() else {
            resultMessage = "Could not find a window to present the file picker from."
            return
        }
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.data])
        picker.allowsMultipleSelection = true
        picker.shouldShowFileExtensions = true
        _ = PickerDelegate(picker: picker,
                           onPicked: { urls in readFiles(urls) })
        presenter.present(picker, animated: true)
    }

    private func readFiles(_ urls: [URL]) {
        var combined = pastedText
        var readErrors: [String] = []
        for url in urls {
            let accessed = url.startAccessingSecurityScopedResource()
            defer { if accessed { url.stopAccessingSecurityScopedResource() } }
            do {
                let data = try Data(contentsOf: url)
                let text = String(data: data, encoding: .utf8)
                    ?? String(data: data, encoding: .isoLatin1)
                    ?? ""
                if !text.isEmpty {
                    if !combined.isEmpty { combined += "\n\n" }
                    combined += text
                }
            } catch {
                readErrors.append(url.lastPathComponent + ": " + error.localizedDescription)
            }
        }
        pastedText = combined
        if !readErrors.isEmpty {
            resultMessage = "Could not read: " + readErrors.joined(separator: ", ")
        }
        if !combined.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            Task { await runImport() }
        }
    }

    private func runImport() async {
        isWorking = true
        defer { isWorking = false }
        do {
            let parsed = try PGPService.parseKeys(from: pastedText)
            for rec in parsed { store.add(rec) }
            resultMessage = "Imported \(parsed.count) key\(parsed.count == 1 ? "" : "s")."
            try? await Task.sleep(nanoseconds: 400_000_000)
            dismiss()
        } catch {
            resultMessage = "Import failed: \(error.localizedDescription)"
        }
    }
}

// MARK: - Export

struct ExportKeySheet: View {
    let record: KeyRecord
    let kind: KeysView.ExportKind

    @Environment(\.dismiss) var dismiss

    private var armored: String {
        kind == .privateKey ? (record.armoredPrivate ?? "") : (record.armoredPublic ?? "")
    }
    private var title: String {
        kind == .privateKey ? "Private key" : "Public key"
    }

    var body: some View {
        NavigationStack {
            Form {
                if kind == .privateKey {
                    Section {
                        Label("This contains your secret key. Anyone with this file AND your passphrase can act as you.",
                              systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.orange)
                            .font(.callout)
                    }
                }
                Section {
                    ScrollView {
                        Text(armored)
                            .font(.system(.caption, design: .monospaced))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .textSelection(.enabled)
                    }
                    .frame(minHeight: 200, maxHeight: 380)
                }
                Section {
                    Button {
                        UIPasteboard.general.string = armored
                    } label: {
                        Label("Copy", systemImage: "doc.on.doc")
                    }
                    ShareLink(item: armored, preview: SharePreview("\(title) — \(record.primaryUser.name)")) {
                        Label("Share / Save…", systemImage: "square.and.arrow.up")
                    }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .presentationDetents([.medium, .large])
            .presentationBackground(.regularMaterial)
        }
    }
}

// MARK: - Passphrase

struct PassphraseSheet: View {
    let context: PassphrasePromptContext
    @State private var passphrase = ""
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Unlock the key for \(context.record.primaryUser.name.isEmpty ? context.record.primaryUser.email : context.record.primaryUser.name)")
                        .font(.callout)
                    SecureField("Passphrase", text: $passphrase)
                        .textContentType(.password)
                        .submitLabel(.go)
                        .onSubmit { submit() }
                }
            }
            .navigationTitle("Passphrase")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        context.onResult(nil)
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Unlock") { submit() }
                        .disabled(passphrase.isEmpty)
                }
            }
            .presentationDetents([.fraction(0.35)])
            .presentationBackground(.regularMaterial)
        }
    }

    private func submit() {
        context.onResult(passphrase)
        dismiss()
    }
}
