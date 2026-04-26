import SwiftUI

// MARK: - Mode picker (segmented capsule)

enum NotepadMode: String, CaseIterable, Identifiable {
    case encrypt, decrypt, sign, verify
    var id: String { rawValue }
    var label: String {
        switch self {
        case .encrypt: return "Encrypt"
        case .decrypt: return "Decrypt"
        case .sign: return "Sign"
        case .verify: return "Verify"
        }
    }
    var systemImage: String {
        switch self {
        case .encrypt: return "lock.fill"
        case .decrypt: return "lock.open.fill"
        case .sign: return "signature"
        case .verify: return "checkmark.seal.fill"
        }
    }
}

struct ModePicker: View {
    @Binding var selected: NotepadMode

    var body: some View {
        Picker("Mode", selection: $selected) {
            ForEach(NotepadMode.allCases) { mode in
                Label(mode.label, systemImage: mode.systemImage).tag(mode)
            }
        }
        .pickerStyle(.segmented)
    }
}

// MARK: - Status banner

struct StatusBanner: View {
    enum Kind { case success, warn, error }
    let kind: Kind
    let title: String
    let detail: String?

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon)
                .foregroundStyle(color)
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.callout.weight(.medium))
                if let d = detail {
                    Text(d).font(.caption).foregroundStyle(.secondary).monospaced()
                }
            }
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(color.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12).stroke(color.opacity(0.35), lineWidth: 1)
        )
    }

    private var color: Color {
        switch kind {
        case .success: return .green
        case .warn: return .orange
        case .error: return .red
        }
    }
    private var icon: String {
        switch kind {
        case .success: return "checkmark.circle.fill"
        case .warn: return "exclamationmark.triangle.fill"
        case .error: return "xmark.octagon.fill"
        }
    }
}

// MARK: - Recipient chip strip

struct RecipientChips: View {
    @EnvironmentObject var theme: ThemeManager
    let recipients: [KeyRecord]
    var onRemove: (KeyRecord) -> Void

    var body: some View {
        if recipients.isEmpty {
            Text("No recipients selected")
                .font(.callout).foregroundStyle(.secondary)
                .padding(.vertical, 6)
        } else {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(recipients) { rec in
                        HStack(spacing: 6) {
                            Image(systemName: "person.fill")
                                .font(.caption)
                            Text(rec.primaryUser.name.isEmpty ? rec.primaryUser.email : rec.primaryUser.name)
                                .font(.callout)
                            Button {
                                onRemove(rec)
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.callout)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .padding(.leading, 10).padding(.trailing, 6).padding(.vertical, 6)
                        .background {
                            Capsule().fill(.ultraThinMaterial)
                            Capsule().fill(theme.current.accentColor.opacity(0.15))
                        }
                        .overlay(Capsule().stroke(theme.current.accentColor.opacity(0.30), lineWidth: 1))
                    }
                }
            }
        }
    }
}

// MARK: - Glass section card

/// A common card shape used in both tabs. Liquid Glass background with theme tint.
struct GlassCard<Content: View>: View {
    @EnvironmentObject var theme: ThemeManager
    var title: String?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let title {
                Text(title.uppercased())
                    .font(.caption.weight(.semibold))
                    .tracking(0.6)
                    .foregroundStyle(theme.current.accentColor.opacity(0.75))
            }
            content
        }
        .padding(16)
        .background {
            RoundedRectangle(cornerRadius: 18).fill(.ultraThinMaterial)
            RoundedRectangle(cornerRadius: 18).fill(theme.current.accentColor.opacity(0.08))
        }
        .overlay(
            RoundedRectangle(cornerRadius: 18).stroke(theme.current.accentColor.opacity(0.25), lineWidth: 1)
        )
    }
}

// MARK: - Settings view

struct SettingsView: View {
    @EnvironmentObject var biometric: BiometricGate
    @EnvironmentObject var store: KeyStore
    @EnvironmentObject var theme: ThemeManager

    var body: some View {
        NavigationStack {
            Form {
                Section("Security") {
                    Toggle(isOn: $biometric.requireUnlock) {
                        Label("Require Face ID / Touch ID", systemImage: "faceid")
                    }
                    Text("When on, NatePad locks whenever you leave the app and requires biometric authentication to re-open.")
                        .font(.caption).foregroundStyle(.secondary)
                }

                Section("Theme") {
                    HStack(spacing: 0) {
                        ForEach(AppTheme.allCases) { t in
                            ThemeSwatch(
                                theme: t,
                                isSelected: theme.current == t,
                                onTap: { theme.set(t) }
                            )
                            .frame(maxWidth: .infinity)
                        }
                    }
                    .padding(.vertical, 8)
                }

                Section("About") {
                    LabeledContent("Stored keys", value: "\(store.keys.count)")
                    LabeledContent("Engine", value: "ObjectivePGP")
                    Link("Source on GitHub", destination: URL(string: "https://github.com/nphil/natepad-ios")!)
                }

                Section {
                    Text("Private keys are passphrase-encrypted by OpenPGP and stored in the iOS Keychain (WhenUnlockedThisDeviceOnly). Always export a backup before relying on this app.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .scrollContentBackground(.hidden)
            .background(ThemeBackground().ignoresSafeArea())
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) { BrandMark() }
            }
        }
    }
}

// `.buttonStyle(.glass)` and `.buttonStyle(.glassProminent)` are native iOS 26 SwiftUI styles.
// No custom definitions needed — using the system ones directly.
