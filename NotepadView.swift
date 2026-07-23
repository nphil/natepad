import SwiftUI
import UIKit
import UniformTypeIdentifiers

// MARK: - Notepad Navigation Destinations

enum NotepadDestination: Hashable {
    case encrypt, decrypt, sign, verify
}

// MARK: - Notepad View Dashboard

struct NotepadView: View {
    @EnvironmentObject var store: KeyStore
    @EnvironmentObject var theme: ThemeManager

    @State private var path: [NotepadDestination] = []
    @State private var prefillText: String? = nil
    @State private var showImportSheet = false
    @State private var importPrefill = ""
    @State private var clipboardHasText = UIPasteboard.general.hasStrings
    @State private var clipboardNote: String? = nil

    private let columns = [
        GridItem(.flexible(), spacing: 14),
        GridItem(.flexible(), spacing: 14)
    ]

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(spacing: 20) {
                    // App Brand Header Card
                    VStack(spacing: 12) {
                        Image(systemName: "lock.shield.fill")
                            .font(.system(size: 52))
                            .foregroundStyle(theme.current.accentColor.gradient)
                            
                        Text("NatePad PGP")
                            .font(.title2.weight(.bold))
                            .foregroundStyle(theme.current.foregroundColor)
                            
                        Text("Securely encrypt, decrypt, sign, and verify messages with bank-grade security on-device.")
                            .font(.callout)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 16)
                    }
                    .padding(24)
                    .frame(maxWidth: .infinity)
                    .background {
                        RoundedRectangle(cornerRadius: 20)
                            .fill(.ultraThinMaterial)
                    }
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(theme.current.accentColor.opacity(0.18), lineWidth: 1)
                    )
                    .padding(.top, 10)
                    
                    // Warning Banner if no keys
                    if store.keys.isEmpty {
                        StatusBanner(
                            kind: .warn,
                            title: "No PGP Keys Found",
                            detail: "Generate or import a key pair under the Keys tab to enable all PGP features."
                        )
                    }

                    // Clipboard quick action
                    if clipboardHasText {
                        Button {
                            checkClipboard()
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "doc.on.clipboard")
                                    .font(.title3)
                                    .foregroundStyle(theme.current.accentColor)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Check Clipboard")
                                        .font(.callout.weight(.semibold))
                                        .foregroundStyle(.primary)
                                    Text("Open a copied PGP message, signature, or key")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(.secondary)
                            }
                            .padding(14)
                            .frame(maxWidth: .infinity)
                            .background {
                                RoundedRectangle(cornerRadius: 16)
                                    .fill(.ultraThinMaterial)
                            }
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(theme.current.accentColor.opacity(0.18), lineWidth: 1)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                    if let clipboardNote {
                        Text(clipboardNote)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    // 2x2 Grid of Actions
                    LazyVGrid(columns: columns, spacing: 14) {
                        NavigationLink(value: NotepadDestination.encrypt) {
                            DashboardButton(
                                title: "Encrypt",
                                description: "Secure a message for recipients",
                                systemImage: "lock.fill",
                                color: theme.current.accentColor
                            )
                        }
                        .buttonStyle(.plain)
                        
                        NavigationLink(value: NotepadDestination.decrypt) {
                            DashboardButton(
                                title: "Decrypt",
                                description: "Open an encrypted message",
                                systemImage: "lock.open.fill",
                                color: theme.current.secondaryColor
                            )
                        }
                        .buttonStyle(.plain)
                        
                        NavigationLink(value: NotepadDestination.sign) {
                            DashboardButton(
                                title: "Sign",
                                description: "Digitally sign a plaintext message",
                                systemImage: "signature",
                                color: theme.current.accentColor
                            )
                        }
                        .buttonStyle(.plain)
                        
                        NavigationLink(value: NotepadDestination.verify) {
                            DashboardButton(
                                title: "Verify",
                                description: "Verify a message's signature",
                                systemImage: "checkmark.seal.fill",
                                color: theme.current.secondaryColor
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 30)
            }
            .background(theme.current.backgroundColor.ignoresSafeArea())
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) { BrandMark() }
            }
            .navigationDestination(for: NotepadDestination.self) { destination in
                switch destination {
                case .encrypt:
                    EncryptWorkflow()
                case .decrypt:
                    DecryptWorkflow(initialText: prefillText ?? "")
                case .sign:
                    SignWorkflow()
                case .verify:
                    VerifyWorkflow(initialText: prefillText ?? "")
                }
            }
            .onChange(of: path) { _, newPath in
                if newPath.isEmpty { prefillText = nil }
            }
            .onAppear {
                clipboardHasText = UIPasteboard.general.hasStrings
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
                clipboardHasText = UIPasteboard.general.hasStrings
                clipboardNote = nil
            }
            .sheet(isPresented: $showImportSheet) {
                ImportKeySheet(initialText: importPrefill)
                    .environmentObject(store)
            }
        }
    }

    /// Reads the pasteboard (user-initiated, so the system paste notice is expected)
    /// and routes whatever PGP block it finds to the right workflow.
    private func checkClipboard() {
        clipboardNote = nil
        guard let text = UIPasteboard.general.string,
              !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            clipboardNote = "The clipboard has no text."
            return
        }
        switch PGPContentKind.detect(in: text) {
        case .encryptedMessage:
            prefillText = text
            path.append(.decrypt)
        case .signedMessage:
            prefillText = text
            path.append(.verify)
        case .publicKey, .privateKey:
            importPrefill = text
            showImportSheet = true
        case nil:
            clipboardNote = "No PGP block found on the clipboard."
        }
    }
}

// MARK: - Dashboard Button

struct DashboardButton: View {
    let title: String
    let description: String
    let systemImage: String
    let color: Color
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: systemImage)
                    .font(.title2.weight(.bold))
                    .foregroundStyle(.white)
                    .padding(10)
                    .background(color, in: RoundedRectangle(cornerRadius: 12))
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.secondary)
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline.weight(.bold))
                    .foregroundStyle(.primary)
                Text(description)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
        }
        .padding(16)
        .frame(maxHeight: .infinity)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(.ultraThinMaterial)
        }
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(color.opacity(0.18), lineWidth: 1)
        )
    }
}

// MARK: - Encrypt Workflow

struct EncryptWorkflow: View {
    @EnvironmentObject var store: KeyStore
    @EnvironmentObject var theme: ThemeManager
    @Environment(\.dismiss) var dismiss

    @State private var inputText = ""
    @State private var recipientIDs: [String] = []
    @State private var signerID: String? = nil
    @State private var armoredOutput = ""
    @State private var isWorking = false
    @State private var errorMessage: String? = nil
    @State private var showResultSheet = false
    
    @State private var passphrasePrompt: PassphrasePromptContext? = nil

    private var selectedRecipients: [KeyRecord] {
        recipientIDs.compactMap { store.key(id: $0) }
    }
    private var availableRecipients: [KeyRecord] {
        store.publicKeys.filter { rec in !recipientIDs.contains(rec.id) }
    }

    var body: some View {
        VStack(spacing: 14) {
            // Recipients card
            GlassSection("Recipients") {
                if recipientIDs.isEmpty {
                    Text("Select who can decrypt this message.")
                        .font(.caption).foregroundStyle(.secondary)
                } else {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(selectedRecipients) { rec in
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(rec.primaryUser.name.isEmpty ? "(unnamed)" : rec.primaryUser.name)
                                        .font(.callout.weight(.medium))
                                    if !rec.primaryUser.email.isEmpty {
                                        Text(rec.primaryUser.email)
                                            .font(.caption2).foregroundStyle(.secondary)
                                    }
                                }
                                Spacer()
                                Button(role: .destructive) {
                                    recipientIDs.removeAll { $0 == rec.id }
                                } label: {
                                    Image(systemName: "minus.circle.fill").foregroundStyle(.red)
                                }
                            }
                        }
                    }
                }
                
                if !availableRecipients.isEmpty {
                    Menu {
                        ForEach(availableRecipients) { rec in
                            Button(rec.label) { recipientIDs.append(rec.id) }
                        }
                    } label: {
                        HStack {
                            Label("Add Recipient", systemImage: "plus.circle.fill")
                                .foregroundStyle(theme.current.accentColor)
                            Spacer()
                            Image(systemName: "chevron.up.chevron.down").font(.caption2).foregroundStyle(.secondary)
                        }
                    }
                } else if store.publicKeys.isEmpty {
                    Text("No public keys available. Import some first.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }

            // Signing key card
            GlassSection("Signing Key (Optional)") {
                HStack {
                    Text("Sign with").font(.callout)
                    Spacer()
                    Picker("Sign with", selection: $signerID) {
                        Text("Don't sign").tag(String?.none)
                        ForEach(store.privateKeys) { rec in
                            Text(rec.label).lineLimit(1).tag(String?.some(rec.id))
                        }
                    }
                    .pickerStyle(.menu)
                    .tint(theme.current.accentColor)
                    .lineLimit(1)
                    .frame(maxWidth: 200)
                }
            }

            // Plaintext Message Editor
            GlassSection("Plaintext Message") {
                ZStack(alignment: .topLeading) {
                    if inputText.isEmpty {
                        Text("Enter message to encrypt...")
                            .font(.callout.monospaced())
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 8)
                            .allowsHitTesting(false)
                    }
                    TextEditor(text: $inputText)
                        .font(.system(.callout, design: .monospaced))
                        .frame(height: 120)
                        .scrollContentBackground(.hidden)
                }
                
                Divider()
                
                HStack {
                    Button {
                        if let s = UIPasteboard.general.string {
                            inputText = s
                        }
                    } label: {
                        Label("Paste", systemImage: "doc.on.clipboard")
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                    
                    Button {
                        importFile(into: $inputText)
                    } label: {
                        Label("Import File", systemImage: "doc.badge.plus")
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                    
                    Spacer()
                    
                    Text("\(inputText.count) ch • \(wordCount(inputText)) w")
                        .font(.caption2).foregroundStyle(.secondary)
                }
            }

            if let errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
                    .font(.caption)
                    .padding(.horizontal, 4)
            }

            Spacer()

            // Main Action Button
            Button {
                Task { await runEncrypt() }
            } label: {
                Group {
                    if isWorking {
                        ProgressView()
                    } else {
                        Label(signerID == nil ? "Encrypt Message" : "Encrypt & Sign Message", systemImage: "lock.fill")
                            .font(.body.weight(.semibold))
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.glassProminent)
            .controlSize(.large)
            .disabled(inputText.isEmpty || recipientIDs.isEmpty || isWorking)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
        .background(theme.current.backgroundColor.ignoresSafeArea())
        .contentShape(Rectangle())
        .onTapGesture { UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil) }
        .navigationTitle("Encrypt")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showResultSheet) {
            NavigationStack {
                VStack(spacing: 20) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 60))
                        .foregroundStyle(.green.gradient)
                        .padding(.top, 24)
                        
                    Text("Encryption Complete")
                        .font(.title2.weight(.bold))
                        
                    Text(signerID == nil
                         ? "The message has been securely encrypted with the selected PGP public keys."
                         : "The message has been signed with your key and encrypted with the selected PGP public keys.")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                        
                    VStack(alignment: .trailing, spacing: 8) {
                        ScrollView {
                            Text(armoredOutput)
                                .font(.system(.caption, design: .monospaced))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .textSelection(.enabled)
                                .padding(12)
                        }
                        .frame(maxHeight: 280)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(.ultraThinMaterial)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(theme.current.accentColor.opacity(0.2), lineWidth: 1)
                        )
                        
                        HStack {
                            Button {
                                UIPasteboard.general.string = armoredOutput
                            } label: {
                                Label("Copy PGP Block", systemImage: "doc.on.doc")
                            }
                            .buttonStyle(.glass)
                            .controlSize(.small)
                            
                            ShareLink(item: armoredOutput) {
                                Label("Share", systemImage: "square.and.arrow.up")
                            }
                            .buttonStyle(.glass)
                            .controlSize(.small)
                        }
                    }
                    .padding(.horizontal, 16)
                    
                    Spacer()
                    
                    Button {
                        showResultSheet = false
                        dismiss()
                    } label: {
                        Text("Done")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)
                    .controlSize(.large)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
                .background(theme.current.backgroundColor.ignoresSafeArea())
                .navigationTitle("Encrypted Result")
                .navigationBarTitleDisplayMode(.inline)
            }
        }
        .sheet(item: $passphrasePrompt) { ctx in
            PassphraseSheet(context: ctx)
        }
    }

    private func wordCount(_ text: String) -> Int {
        text.split { $0.isWhitespace || $0.isNewline }.count
    }

    private func importFile(into binding: Binding<String>) {
        guard let presenter = topMostViewController() else { return }
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.item], asCopy: true)
        picker.allowsMultipleSelection = false
        _ = PickerDelegate(picker: picker, onPicked: { urls in
            guard let url = urls.first else { return }
            let accessed = url.startAccessingSecurityScopedResource()
            defer { if accessed { url.stopAccessingSecurityScopedResource() } }
            if let data = try? Data(contentsOf: url),
               let text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) {
                binding.wrappedValue = text
            }
        })
        presenter.present(picker, animated: true)
    }

    private func runEncrypt() async {
        errorMessage = nil
        isWorking = true
        defer { isWorking = false }

        do {
            let input = inputText
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
            armoredOutput = armored
            showResultSheet = true
        } catch {
            errorMessage = error.localizedDescription
        }
    }

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

// MARK: - Decrypt Workflow

struct DecryptWorkflow: View {
    @EnvironmentObject var store: KeyStore
    @EnvironmentObject var theme: ThemeManager
    @Environment(\.dismiss) var dismiss

    @State private var inputText: String
    @State private var decryptKeyID: String? = nil
    @State private var decryptedText = ""
    @State private var signatureStatus: PGPService.SignatureStatus = .notSigned
    @State private var isWorking = false
    @State private var errorMessage: String? = nil
    @State private var showResultSheet = false

    @State private var passphrasePrompt: PassphrasePromptContext? = nil

    init(initialText: String = "") {
        _inputText = State(initialValue: initialText)
    }

    var body: some View {
        VStack(spacing: 14) {
            // Decryption key card
            GlassSection("Decryption Key") {
                HStack {
                    Text("Decrypt with").font(.callout)
                    Spacer()
                    Picker("Decrypt with", selection: $decryptKeyID) {
                        Text("Auto-detect").tag(String?.none)
                        ForEach(store.privateKeys) { rec in
                            Text(rec.label).lineLimit(1).tag(String?.some(rec.id))
                        }
                    }
                    .pickerStyle(.menu)
                    .tint(theme.current.accentColor)
                    .lineLimit(1)
                    .frame(maxWidth: 200)
                }
            }

            // Encrypted Message Editor
            GlassSection("Encrypted Message (PGP Message)") {
                ZStack(alignment: .topLeading) {
                    if inputText.isEmpty {
                        Text("Paste PGP message starting with -----BEGIN PGP MESSAGE-----")
                            .font(.callout.monospaced())
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 8)
                            .allowsHitTesting(false)
                    }
                    TextEditor(text: $inputText)
                        .font(.system(.callout, design: .monospaced))
                        .frame(height: 180)
                        .scrollContentBackground(.hidden)
                }
                
                Divider()
                
                HStack {
                    Button {
                        if let s = UIPasteboard.general.string {
                            inputText = s
                        }
                    } label: {
                        Label("Paste", systemImage: "doc.on.clipboard")
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                    
                    Button {
                        importFile(into: $inputText)
                    } label: {
                        Label("Import File", systemImage: "doc.badge.plus")
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                    
                    Spacer()
                    
                    Text("\(inputText.count) chars")
                        .font(.caption2).foregroundStyle(.secondary)
                }
            }

            if let errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
                    .font(.caption)
                    .padding(.horizontal, 4)
            }

            Spacer()

            // Main Action Button
            Button {
                Task { await runDecrypt() }
            } label: {
                Group {
                    if isWorking {
                        ProgressView()
                    } else {
                        Label("Decrypt Message", systemImage: "lock.open.fill")
                            .font(.body.weight(.semibold))
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.glassProminent)
            .controlSize(.large)
            .disabled(inputText.isEmpty || isWorking)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
        .background(theme.current.backgroundColor.ignoresSafeArea())
        .contentShape(Rectangle())
        .onTapGesture { UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil) }
        .navigationTitle("Decrypt")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showResultSheet) {
            NavigationStack {
                VStack(spacing: 20) {
                    Image(systemName: "lock.open.fill")
                        .font(.system(size: 60))
                        .foregroundStyle(theme.current.accentColor.gradient)
                        .padding(.top, 24)
                        
                    Text("Decryption Successful")
                        .font(.title2.weight(.bold))

                    switch signatureStatus {
                    case .valid:
                        StatusBanner(kind: .success,
                                     title: "Signature verified",
                                     detail: "Signed by a key in your keyring.")
                            .padding(.horizontal, 16)
                    case .invalid(let reason):
                        StatusBanner(kind: .warn,
                                     title: "Signature not verified",
                                     detail: reason)
                            .padding(.horizontal, 16)
                    case .notSigned:
                        Text("This message is not signed.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    VStack(alignment: .trailing, spacing: 8) {
                        ScrollView {
                            Text(decryptedText)
                                .font(.system(.body, design: .monospaced))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .textSelection(.enabled)
                                .padding(12)
                        }
                        .frame(maxHeight: 280)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(.ultraThinMaterial)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(theme.current.accentColor.opacity(0.2), lineWidth: 1)
                        )
                        
                        HStack {
                            Button {
                                SensitivePasteboard.copy(decryptedText)
                            } label: {
                                Label("Copy Plaintext", systemImage: "doc.on.doc")
                            }
                            .buttonStyle(.glass)
                            .controlSize(.small)

                            ShareLink(item: decryptedText) {
                                Label("Share", systemImage: "square.and.arrow.up")
                            }
                            .buttonStyle(.glass)
                            .controlSize(.small)
                        }
                        Text("Copies clear from the clipboard after \(Int(SensitivePasteboard.clearAfterSeconds)) s.")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.horizontal, 16)
                    
                    Spacer()
                    
                    Button {
                        showResultSheet = false
                        dismiss()
                    } label: {
                        Text("Done")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)
                    .controlSize(.large)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
                .background(theme.current.backgroundColor.ignoresSafeArea())
                .navigationTitle("Decrypted Message")
                .navigationBarTitleDisplayMode(.inline)
            }
        }
        .sheet(item: $passphrasePrompt) { ctx in
            PassphraseSheet(context: ctx)
        }
    }

    private func importFile(into binding: Binding<String>) {
        guard let presenter = topMostViewController() else { return }
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.item], asCopy: true)
        picker.allowsMultipleSelection = false
        _ = PickerDelegate(picker: picker, onPicked: { urls in
            guard let url = urls.first else { return }
            let accessed = url.startAccessingSecurityScopedResource()
            defer { if accessed { url.stopAccessingSecurityScopedResource() } }
            if let data = try? Data(contentsOf: url),
               let text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) {
                binding.wrappedValue = text
            }
        })
        presenter.present(picker, animated: true)
    }

    private func runDecrypt() async {
        errorMessage = nil
        isWorking = true
        defer { isWorking = false }

        do {
            let input = inputText
            guard !input.isEmpty else { throw PGPService.PGPError.parseFailed("Nothing to decrypt") }

            let keysToTry: [KeyRecord]
            if let id = decryptKeyID, let rec = store.key(id: id) {
                keysToTry = [rec]
            } else {
                guard !store.privateKeys.isEmpty else {
                    throw PGPService.PGPError.parseFailed("No private keys — import or generate a key pair first.")
                }
                keysToTry = store.privateKeys
            }

            // First attempt without passphrases. ObjectivePGP tells us (via the
            // callback) which key the message actually needs, so the user is only
            // prompted for that one — not for every private key in the keyring.
            var requestedID: String? = nil
            do {
                let result = try PGPService.decrypt(
                    armoredMessage: input,
                    using: keysToTry,
                    verificationKeys: store.publicKeys,
                    passphraseProvider: { rec in
                        requestedID = rec.id
                        return nil
                    }
                )
                decryptedText = result.plaintext
                signatureStatus = result.signature
                showResultSheet = true
                return
            } catch PGPService.PGPError.passphraseRequired {
                // Prompt for the key that was requested, below.
            }

            guard let needed = requestedID.flatMap({ store.key(id: $0) }) ?? keysToTry.first else {
                throw PGPService.PGPError.noMatchingDecryptionKey
            }
            guard let passphrase = await askPassphrase(for: needed) else { return } // cancelled

            let result = try PGPService.decrypt(
                armoredMessage: input,
                using: keysToTry,
                verificationKeys: store.publicKeys,
                passphraseProvider: { rec in rec.id == needed.id ? passphrase : nil }
            )
            decryptedText = result.plaintext
            signatureStatus = result.signature
            showResultSheet = true
        } catch {
            errorMessage = error.localizedDescription
        }
    }

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

// MARK: - Sign Workflow

struct SignWorkflow: View {
    @EnvironmentObject var store: KeyStore
    @EnvironmentObject var theme: ThemeManager
    @Environment(\.dismiss) var dismiss

    @State private var inputText = ""
    @State private var signerID: String? = nil
    @State private var armoredOutput = ""
    @State private var isWorking = false
    @State private var errorMessage: String? = nil
    @State private var showResultSheet = false
    
    @State private var passphrasePrompt: PassphrasePromptContext? = nil

    var body: some View {
        VStack(spacing: 14) {
            // Signing key card
            GlassSection("Signing Key") {
                HStack {
                    Text("Sign with").font(.callout)
                    Spacer()
                    Picker("Sign with", selection: $signerID) {
                        Text("Select a key").tag(String?.none)
                        ForEach(store.privateKeys) { rec in
                            Text(rec.label).lineLimit(1).tag(String?.some(rec.id))
                        }
                    }
                    .pickerStyle(.menu)
                    .tint(theme.current.accentColor)
                    .lineLimit(1)
                    .frame(maxWidth: 200)
                }
            }

            // Plaintext Message Editor
            GlassSection("Plaintext Message") {
                ZStack(alignment: .topLeading) {
                    if inputText.isEmpty {
                        Text("Enter the message text to sign...")
                            .font(.callout.monospaced())
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 8)
                            .allowsHitTesting(false)
                    }
                    TextEditor(text: $inputText)
                        .font(.system(.callout, design: .monospaced))
                        .frame(height: 180)
                        .scrollContentBackground(.hidden)
                }
                
                Divider()
                
                HStack {
                    Button {
                        if let s = UIPasteboard.general.string {
                            inputText = s
                        }
                    } label: {
                        Label("Paste", systemImage: "doc.on.clipboard")
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                    
                    Button {
                        importFile(into: $inputText)
                    } label: {
                        Label("Import File", systemImage: "doc.badge.plus")
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                    
                    Spacer()
                    
                    Text("\(inputText.count) chars")
                        .font(.caption2).foregroundStyle(.secondary)
                }
            }

            if let errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
                    .font(.caption)
                    .padding(.horizontal, 4)
            }

            Spacer()

            // Main Action Button
            Button {
                Task { await runSign() }
            } label: {
                Group {
                    if isWorking {
                        ProgressView()
                    } else {
                        Label("Sign Message", systemImage: "signature")
                            .font(.body.weight(.semibold))
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.glassProminent)
            .controlSize(.large)
            .disabled(inputText.isEmpty || signerID == nil || isWorking)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
        .background(theme.current.backgroundColor.ignoresSafeArea())
        .contentShape(Rectangle())
        .onTapGesture { UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil) }
        .navigationTitle("Sign")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showResultSheet) {
            NavigationStack {
                VStack(spacing: 20) {
                    Image(systemName: "signature")
                        .font(.system(size: 60))
                        .foregroundStyle(theme.current.accentColor.gradient)
                        .padding(.top, 24)
                        
                    Text("Signature Generated")
                        .font(.title2.weight(.bold))
                        
                    VStack(alignment: .trailing, spacing: 8) {
                        ScrollView {
                            Text(armoredOutput)
                                .font(.system(.caption, design: .monospaced))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .textSelection(.enabled)
                                .padding(12)
                        }
                        .frame(maxHeight: 280)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(.ultraThinMaterial)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(theme.current.accentColor.opacity(0.2), lineWidth: 1)
                        )
                        
                        HStack {
                            Button {
                                UIPasteboard.general.string = armoredOutput
                            } label: {
                                Label("Copy PGP Block", systemImage: "doc.on.doc")
                            }
                            .buttonStyle(.glass)
                            .controlSize(.small)
                            
                            ShareLink(item: armoredOutput) {
                                Label("Share", systemImage: "square.and.arrow.up")
                            }
                            .buttonStyle(.glass)
                            .controlSize(.small)
                        }
                    }
                    .padding(.horizontal, 16)
                    
                    Spacer()
                    
                    Button {
                        showResultSheet = false
                        dismiss()
                    } label: {
                        Text("Done")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)
                    .controlSize(.large)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
                .background(theme.current.backgroundColor.ignoresSafeArea())
                .navigationTitle("Signed Message")
                .navigationBarTitleDisplayMode(.inline)
            }
        }
        .sheet(item: $passphrasePrompt) { ctx in
            PassphraseSheet(context: ctx)
        }
    }

    private func importFile(into binding: Binding<String>) {
        guard let presenter = topMostViewController() else { return }
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.item], asCopy: true)
        picker.allowsMultipleSelection = false
        _ = PickerDelegate(picker: picker, onPicked: { urls in
            guard let url = urls.first else { return }
            let accessed = url.startAccessingSecurityScopedResource()
            defer { if accessed { url.stopAccessingSecurityScopedResource() } }
            if let data = try? Data(contentsOf: url),
               let text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) {
                binding.wrappedValue = text
            }
        })
        presenter.present(picker, animated: true)
    }

    private func runSign() async {
        errorMessage = nil
        isWorking = true
        defer { isWorking = false }

        do {
            let input = inputText
            guard !input.isEmpty else { throw PGPService.PGPError.parseFailed("Nothing to sign") }
            guard let id = signerID, let rec = store.key(id: id) else {
                throw PGPService.PGPError.parseFailed("Pick a signing key")
            }
            guard let passphrase = await askPassphrase(for: rec) else { return }

            let armored = try PGPService.sign(plaintext: input, with: rec, passphrase: passphrase)
            armoredOutput = armored
            showResultSheet = true
        } catch {
            errorMessage = error.localizedDescription
        }
    }

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

// MARK: - Verify Workflow

struct VerifyWorkflow: View {
    @EnvironmentObject var store: KeyStore
    @EnvironmentObject var theme: ThemeManager
    @Environment(\.dismiss) var dismiss

    @State private var inputText: String
    @State private var verifiedText = ""
    @State private var isWorking = false
    @State private var errorMessage: String? = nil
    @State private var showResultSheet = false

    init(initialText: String = "") {
        _inputText = State(initialValue: initialText)
    }

    var body: some View {
        VStack(spacing: 14) {
            // Signed PGP Message Editor
            GlassSection("Signed PGP Message") {
                ZStack(alignment: .topLeading) {
                    if inputText.isEmpty {
                        Text("Paste signed PGP message starting with -----BEGIN PGP MESSAGE----- or -----BEGIN PGP SIGNED MESSAGE-----")
                            .font(.callout.monospaced())
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 8)
                            .allowsHitTesting(false)
                    }
                    TextEditor(text: $inputText)
                        .font(.system(.callout, design: .monospaced))
                        .frame(height: 220)
                        .scrollContentBackground(.hidden)
                }
                
                Divider()
                
                HStack {
                    Button {
                        if let s = UIPasteboard.general.string {
                            inputText = s
                        }
                    } label: {
                        Label("Paste", systemImage: "doc.on.clipboard")
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                    
                    Button {
                        importFile(into: $inputText)
                    } label: {
                        Label("Import File", systemImage: "doc.badge.plus")
                    }
                    .buttonStyle(.glass)
                    .controlSize(.small)
                    
                    Spacer()
                    
                    Text("\(inputText.count) chars")
                        .font(.caption2).foregroundStyle(.secondary)
                }
            }

            if let errorMessage {
                Text(errorMessage)
                    .foregroundStyle(.red)
                    .font(.caption)
                    .padding(.horizontal, 4)
            }

            Spacer()

            // Main Action Button
            Button {
                Task { await runVerify() }
            } label: {
                Group {
                    if isWorking {
                        ProgressView()
                    } else {
                        Label("Verify Message", systemImage: "checkmark.seal.fill")
                            .font(.body.weight(.semibold))
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.glassProminent)
            .controlSize(.large)
            .disabled(inputText.isEmpty || isWorking)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
        .background(theme.current.backgroundColor.ignoresSafeArea())
        .contentShape(Rectangle())
        .onTapGesture { UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil) }
        .navigationTitle("Verify")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showResultSheet) {
            NavigationStack {
                VStack(spacing: 20) {
                    Image(systemName: "checkmark.seal.fill")
                        .font(.system(size: 60))
                        .foregroundStyle(.green.gradient)
                        .padding(.top, 24)
                        
                    Text("Signature is VALID")
                        .font(.title2.weight(.bold))
                        
                    Text("The signature on this message matches a public key in your keychain.")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                        
                    VStack(alignment: .trailing, spacing: 8) {
                        ScrollView {
                            Text(verifiedText)
                                .font(.system(.body, design: .monospaced))
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .textSelection(.enabled)
                                .padding(12)
                        }
                        .frame(maxHeight: 280)
                        .background(
                            RoundedRectangle(cornerRadius: 12)
                                .fill(.ultraThinMaterial)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(theme.current.accentColor.opacity(0.2), lineWidth: 1)
                        )
                        
                        HStack {
                            Button {
                                SensitivePasteboard.copy(verifiedText)
                            } label: {
                                Label("Copy Plaintext", systemImage: "doc.on.doc")
                            }
                            .buttonStyle(.glass)
                            .controlSize(.small)

                            ShareLink(item: verifiedText) {
                                Label("Share", systemImage: "square.and.arrow.up")
                            }
                            .buttonStyle(.glass)
                            .controlSize(.small)
                        }
                        Text("Copies clear from the clipboard after \(Int(SensitivePasteboard.clearAfterSeconds)) s.")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.horizontal, 16)
                    
                    Spacer()
                    
                    Button {
                        showResultSheet = false
                        dismiss()
                    } label: {
                        Text("Done")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)
                    .controlSize(.large)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
                .background(theme.current.backgroundColor.ignoresSafeArea())
                .navigationTitle("Verified Message")
                .navigationBarTitleDisplayMode(.inline)
            }
        }
    }

    private func importFile(into binding: Binding<String>) {
        guard let presenter = topMostViewController() else { return }
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.item], asCopy: true)
        picker.allowsMultipleSelection = false
        _ = PickerDelegate(picker: picker, onPicked: { urls in
            guard let url = urls.first else { return }
            let accessed = url.startAccessingSecurityScopedResource()
            defer { if accessed { url.stopAccessingSecurityScopedResource() } }
            if let data = try? Data(contentsOf: url),
               let text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) {
                binding.wrappedValue = text
            }
        })
        presenter.present(picker, animated: true)
    }

    private func runVerify() async {
        errorMessage = nil
        isWorking = true
        defer { isWorking = false }

        do {
            let input = inputText
            guard !input.isEmpty else { throw PGPService.PGPError.parseFailed("Nothing to verify") }
            let result = try PGPService.verify(armoredMessage: input, using: store.publicKeys)
            verifiedText = result
            showResultSheet = true
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

// MARK: - Passphrase Prompt Context

/// Context object passed into the PassphraseSheet.
struct PassphrasePromptContext: Identifiable {
    let id = UUID()
    let record: KeyRecord
    let onResult: (String?) -> Void
}

struct GlassSection<Content: View>: View {
    let title: String
    let content: Content
    
    init(_ title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.secondary)
                .padding(.horizontal, 4)
            
            VStack(alignment: .leading, spacing: 10) {
                content
            }
            .padding(12)
            .background(Color.primary.opacity(0.04))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.primary.opacity(0.08), lineWidth: 1)
            )
        }
    }
}
