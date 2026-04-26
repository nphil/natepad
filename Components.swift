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
                        .background(.ultraThinMaterial, in: Capsule())
                        .overlay(Capsule().stroke(.white.opacity(0.08), lineWidth: 1))
                    }
                }
            }
        }
    }
}

// MARK: - Glass section card

/// A common card shape used in both tabs. Liquid Glass background.
struct GlassCard<Content: View>: View {
    var title: String?
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let title {
                Text(title.uppercased())
                    .font(.caption.weight(.semibold))
                    .tracking(0.6)
                    .foregroundStyle(.secondary)
            }
            content
        }
        .padding(16)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 18))
        .overlay(
            RoundedRectangle(cornerRadius: 18).stroke(.white.opacity(0.06), lineWidth: 1)
        )
    }
}

// MARK: - Plain Settings view

struct SettingsView: View {
    @EnvironmentObject var biometric: BiometricGate
    @EnvironmentObject var store: KeyStore

    var body: some View {
        NavigationStack {
            Form {
                Section("Security") {
                    Toggle(isOn: $biometric.requireUnlock) {
                        Label("Require Face ID / Touch ID", systemImage: "faceid")
                    }
                    Text("When on, Natepad requires biometric authentication every time you open the app.")
                        .font(.caption).foregroundStyle(.secondary)
                }
                Section("About") {
                    LabeledContent("Stored keys", value: "\(store.keys.count)")
                    LabeledContent("Engine", value: "ObjectivePGP")
                    Link("Source on GitHub", destination: URL(string: "https://github.com")!)
                }
                Section {
                    Text("Private keys are passphrase-encrypted by OpenPGP and stored in the iOS Keychain (WhenUnlockedThisDeviceOnly). Always export a backup before relying on this app.")
                        .font(.caption).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Settings")
        }
    }
}

// MARK: - Custom glass button styles
// `.buttonStyle(.glass)` and `.buttonStyle(.glassProminent)` are NOT real SwiftUI API names.
// These custom styles replicate the look using materials, working on iOS 16+ and looking
// native on iOS 26 (where material backgrounds automatically pick up the Liquid Glass treatment).

struct GlassButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .padding(.horizontal, 14)
            .padding(.vertical, 8)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(.white.opacity(0.1), lineWidth: 0.5)
            )
            .opacity(configuration.isPressed ? 0.6 : 1)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

struct GlassProminentButtonStyle: ButtonStyle {
    @Environment(\.controlSize) var controlSize

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .padding(.horizontal, controlSize == .small ? 10 : 14)
            .padding(.vertical, controlSize == .small ? 6 : 8)
            .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            .foregroundStyle(.white)
            .opacity(configuration.isPressed ? 0.75 : 1)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

extension ButtonStyle where Self == GlassButtonStyle {
    static var glass: GlassButtonStyle { GlassButtonStyle() }
}

extension ButtonStyle where Self == GlassProminentButtonStyle {
    static var glassProminent: GlassProminentButtonStyle { GlassProminentButtonStyle() }
}
