import SwiftUI

@main
struct NatepadApp: App {
    @StateObject private var store = KeyStore()
    @StateObject private var biometric = BiometricGate()

    init() {
        appLog("=== Natepad launched ===")
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(biometric)
                .tint(.indigo)               // accent color across the app
                .preferredColorScheme(.dark) // matches the web version's vibe
        }
    }
}

/// Root view — biometric lock screen, then the tabbed app.
struct RootView: View {
    @EnvironmentObject var biometric: BiometricGate

    var body: some View {
        Group {
            if biometric.requireUnlock && !biometric.unlocked {
                LockScreen()
            } else {
                ContentView()
            }
        }
        .task { await biometric.attemptUnlockOnLaunch() }
    }
}

/// Tab bar root. iOS 26 renders this as a floating Liquid Glass capsule automatically.
struct ContentView: View {
    var body: some View {
        TabView {
            NotepadView()
                .tabItem {
                    Label("Notepad", systemImage: "note.text")
                }

            KeysView()
                .tabItem {
                    Label("Keys", systemImage: "key.fill")
                }

            SettingsView()
                .tabItem {
                    Label("Settings", systemImage: "gearshape.fill")
                }
        }
    }
}

/// Brand mark used in nav titles. Tiny inline SVG-equivalent — drawn with SF Symbols + a gradient.
struct BrandMark: View {
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "lock.shield.fill")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(
                    LinearGradient(
                        colors: [Color(red: 0.78, green: 0.84, blue: 1.0),
                                 Color(red: 0.36, green: 0.49, blue: 0.94)],
                        startPoint: .top, endPoint: .bottom
                    )
                )
            Text("Natepad")
                .font(.system(size: 18, weight: .semibold, design: .default))
        }
    }
}

/// Shown when biometric gate is on and we haven't unlocked yet.
struct LockScreen: View {
    @EnvironmentObject var biometric: BiometricGate

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image(systemName: "lock.shield.fill")
                .font(.system(size: 64))
                .foregroundStyle(.indigo.gradient)
            Text("Natepad is locked")
                .font(.title2.weight(.semibold))
            Text("Authenticate to access your keys")
                .foregroundStyle(.secondary)
            Spacer()
            Button {
                Task { await biometric.unlock() }
            } label: {
                Label("Unlock", systemImage: "faceid")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 4)
            }
            .buttonStyle(.glassProminent)
            .padding(.horizontal, 40)
            Spacer().frame(height: 40)
        }
        .padding()
    }
}
