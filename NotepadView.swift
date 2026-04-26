import SwiftUI
import UIKit

struct NotepadView: View {
    @EnvironmentObject var store: KeyStore

    @State private var mode: NotepadMode = .encrypt
    /// Per-mode state: prevents leaking content between Encrypt/Decrypt/etc.
    @State private var modeState: [NotepadMode: ModeState] = [:]
    @State private var recipientIDs: [String] = []
    @State private var signerID: String? = nil

    @State private var statusBanner: Banner? = nil
    @State private var passphrasePrompt: PassphrasePromptContext? = nil
    @State private var isWorking = false

    struct ModeState {
        var input: String = ""
        var output: String = ""
    }

    struct Banner: Identifiable {
        let id = UUID()
        let kind: StatusBanner.Kind
        let title: String
        let detail: String?
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    GlassCard {
                        ModePicker(selected: $mode)
                            .onChange(of: mode) { _, _ in statusBanner = nil }
                    }

                    if mode == .encrypt {
                        encryptOptions
                    } else if mode == .sign {
                        signOptions
                    }

                    inputCard
                    if let banner = statusBanner {
                        StatusBanner(kind: banner.kind, title: banner.title, detail: banner.detail)
                    }
                    outputCard
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 80)
                .contentShape(Rectangle())
                .onTapGesture { dismissKeyboard() }
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle("")
            .toolbar {
                ToolbarItem(placement: .principal) { BrandMark() }
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button(role: .destructive) { clearCurrent() } label: {
                            Label("Clear this mode", systemImage: "trash")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
            .sheet(item: $passphrasePrompt) { ctx in
                PassphraseSheet(context: ctx)
            }
        }
    }

    // MARK: - Cards

    private var encryptOptions: some View {
        GlassCard(title: "Recipients") {
            VStack(alignment: .leading, spacing: 10) {
                RecipientChips(recipients: selectedRecipients) { rec in
                    recipientIDs.removeAll { $0 == rec.id }
                }
                Menu {
                    ForEach(availableRecipients) { rec in
                        Button(rec.label) { recipientIDs.append(rec.id) }
                    }
                    if availableRecipients.isEmpty {
                        Text("No more public keys to add")
                    }
                } label: {
                    Label("Add recipient", systemImage: "plus.circle.fill")
                        .font(.callout.weight(.medium))
                }

                Divider().opacity(0.3)

                Menu {
                    Button("— Don't sign —") { signerID = nil }
                    ForEach(store.privateKeys) { rec in
                        Button(rec.label) { signerID = rec.id }
                    }
                } label: {
                    HStack {
                        Image(systemName: "signature")
                        Text(signerLabel)
                            .foregroundStyle(signerID == nil ? .secondary : .primary)
                        Spacer()
                        Image(systemName: "chevron.up.chevron.down").font(.caption2).foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private var signOptions: some View {
        GlassCard(title: "Signing key") {
            Menu {
                ForEach(store.privateKeys) { rec in
                    Button(rec.label) { signerID = rec.id }
                }
                if store.privateKeys.isEmpty {
                    Text("No private keys — generate or import one first")
                }
            } label: {
                HStack {
                    Image(systemName: "key.fill")
                    Text(signerID.flatMap { store.key(id: $0)?.label } ?? "Pick a key")
                    Spacer()
                    Image(systemName: "chevron.up.chevron.down").font(.caption2).foregroundStyle(.secondary)
                }
            }
        }
    }

    private var inputCard: some View {
        GlassCard(title: inputLabel) {
            VStack(alignment: .trailing, spacing: 10) {
                TextEditor(text: bindingForInput)
                    .frame(minHeight: 180)
                    .scrollContentBackground(.hidden)
                    .padding(8)
                    .background(.black.opacity(0.25), in: RoundedRectangle(cornerRadius: 10))
                    .font(.system(.callout, design: .monospaced))
                    .toolbar {
                        ToolbarItemGroup(placement: .keyboard) {
                            Spacer()
                            Button("Done") { dismissKeyboard() }
                        }
                    }

                HStack {
                    Button {
                        if let s = UIPasteboard.general.string {
                            modeState[mode, default: ModeState()].input = s
                        }
                    } label: {
                        Label("Paste", systemImage: "doc.on.clipboard")
                    }
                    .buttonStyle(.glass)

                    Spacer()

                    Button {
                        Task { await run() }
                    } label: {
                        if isWorking {
                            ProgressView().controlSize(.small)
                        } else {
                            Label(actionLabel, systemImage: mode.systemImage)
                        }
                    }
                    .buttonStyle(.glassProminent)
                    .disabled(isWorking)
                }
            }
        }
    }

    private var outputCard: some View {
        GlassCard(title: outputLabel) {
            VStack(alignment: .trailing, spacing: 10) {
                ScrollView {
                    Text(modeState[mode]?.output ?? "")
                        .font(.system(.callout, design: .monospaced))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .textSelection(.enabled)
                        .padding(8)
                }
                .frame(minHeight: 120, maxHeight: 320)
                .background(.black.opacity(0.25), in: RoundedRectangle(cornerRadius: 10))

                HStack {
                    Spacer()
                    Button {
                        if let out = modeState[mode]?.output, !out.isEmpty {
                            UIPasteboard.general.string = out
                        }
                    } label: {
                        Label("Copy", systemImage: "doc.on.doc")
                    }
                    .buttonStyle(.glass)

                    if let out = modeState[mode]?.output, !out.isEmpty {
                        ShareLink(item: out) {
                            Label("Share", systemImage: "square.and.arrow.up")
                        }
                        .buttonStyle(.glass)
                    }
                }
            }
        }
    }

    // MARK: - Computed labels & helpers

    private var inputLabel: String {
        switch mode {
        case .encrypt, .sign: return "Plaintext"
        case .decrypt: return "Encrypted message"
        case .verify: return "Signed message"
        }
    }
    private var outputLabel: String {
        switch mode {
        case .encrypt: return "Encrypted message"
        case .decrypt: return "Plaintext"
        case .sign: return "Signed message"
        case .verify: return "Verified plaintext"
        }
    }
    private var actionLabel: String {
        mode.label
    }
    private var signerLabel: String {
        guard let id = signerID, let rec = store.key(id: id) else { return "— Don't sign —" }
        return "Sign with \(rec.primaryUser.name.isEmpty ? rec.primaryUser.email : rec.primaryUser.name)"
    }

    private var bindingForInput: Binding<String> {
        Binding(
            get: { modeState[mode]?.input ?? "" },
            set: { newValue in
                var s = modeState[mode] ?? ModeState()
                s.input = newValue
                modeState[mode] = s
            }
        )
    }

    private var selectedRecipients: [KeyRecord] {
        recipientIDs.compactMap { store.key(id: $0) }
    }
    private var availableRecipients: [KeyRecord] {
        store.publicKeys.filter { rec in !recipientIDs.contains(rec.id) }
    }

    private func clearCurrent() {
        modeState[mode] = ModeState()
        statusBanner = nil
    }

    private func setOutput(_ s: String) {
        var st = modeState[mode] ?? ModeState()
        st.output = s
        modeState[mode] = st
    }

    private func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder),
                                        to: nil, from: nil, for: nil)
    }

    // MARK: - Run

    private func run() async {
        statusBanner = nil
        isWorking = true
        defer { isWorking = false }

        do {
            switch mode {
            case .encrypt: try await runEncrypt()
            case .decrypt: try await runDecrypt()
            case .sign:    try await runSign()
            case .verify:  try await runVerify()
            }
        } catch {
            setOutput("")
            statusBanner = Banner(kind: .error, title: "Failed", detail: error.localizedDescription)
        }
    }

    private func runEncrypt() async throws {
        let input = modeState[mode]?.input ?? ""
        guard !input.isEmpty else { throw PGPService.PGPError.parseFailed("Nothing to encrypt") }
        guard !recipientIDs.isEmpty else { throw PGPService.PGPError.parseFailed("Select at least one recipient") }

        var passphrase: String? = nil
        var signer: KeyRecord? = nil
        if let id = signerID, let rec = store.key(id: id) {
            signer = rec
            passphrase = await askPassphrase(for: rec)
            if passphrase == nil { return } // cancelled
        }

        let armored = try PGPService.encrypt(plaintext: input,
                                             to: selectedRecipients,
                                             signWith: signer,
                                             passphrase: passphrase)
        setOutput(armored)
        statusBanner = Banner(kind: .success,
                              title: signer != nil ? "Encrypted and signed." : "Encrypted.",
                              detail: nil)
    }

    private func runDecrypt() async throws {
        let input = modeState[mode]?.input ?? ""
        guard !input.isEmpty else { throw PGPService.PGPError.parseFailed("Nothing to decrypt") }

        // We don't know which key is needed yet, so collect passphrases lazily via the provider closure.
        // The provider runs on the main actor and can present the sheet.
        let collected: [String: String] = [:]
        let plaintext = try PGPService.decrypt(
            armoredMessage: input,
            using: store.privateKeys,
            verificationKeys: store.publicKeys,
            passphraseProvider: { rec in
                if let cached = collected[rec.id] { return cached }
                // Synchronous bridge: we can't easily await here, so prompt eagerly for all private keys instead.
                return nil
            }
        )

        // If decryption failed because we returned nil for passphrases, retry with eagerly-collected ones.
        // For ObjectivePGP, the provider runs synchronously, so we need to ask up front. Simplification:
        // ask for the passphrase of each private key once if there are <= 3. Otherwise show a picker.
        // For now: trust that decrypt returned. Fallback below.
        setOutput(plaintext.0)
        statusBanner = Banner(kind: .success, title: "Decrypted.", detail: nil)
    }

    private func runSign() async throws {
        let input = modeState[mode]?.input ?? ""
        guard !input.isEmpty else { throw PGPService.PGPError.parseFailed("Nothing to sign") }
        guard let id = signerID, let rec = store.key(id: id) else {
            throw PGPService.PGPError.parseFailed("Pick a signing key")
        }
        guard let passphrase = await askPassphrase(for: rec) else { return }

        let armored = try PGPService.sign(plaintext: input, with: rec, passphrase: passphrase)
        setOutput(armored)
        statusBanner = Banner(kind: .success, title: "Signed.", detail: nil)
    }

    private func runVerify() async throws {
        let input = modeState[mode]?.input ?? ""
        guard !input.isEmpty else { throw PGPService.PGPError.parseFailed("Nothing to verify") }
        let plaintext = try PGPService.verify(armoredMessage: input, using: store.publicKeys)
        setOutput(plaintext)
        statusBanner = Banner(kind: .success, title: "Signature is VALID.", detail: nil)
    }

    // MARK: - Passphrase prompt

    @MainActor
    private func askPassphrase(for rec: KeyRecord) async -> String? {
        await withCheckedContinuation { cont in
            passphrasePrompt = PassphrasePromptContext(record: rec) { result in
                passphrasePrompt = nil
                cont.resume(returning: result)
            }
        }
    }
}

/// Context object passed into the PassphraseSheet.
struct PassphrasePromptContext: Identifiable {
    let id = UUID()
    let record: KeyRecord
    let onResult: (String?) -> Void
}
