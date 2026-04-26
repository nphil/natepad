import SwiftUI
import UIKit

@main
struct NatepadApp: App {
    @StateObject private var store = KeyStore()
    @StateObject private var biometric = BiometricGate()
    @StateObject private var theme = ThemeManager()

    init() {
        // Make navigation bars and form/list backgrounds transparent so the
        // gradient applied to the TabView shows through everywhere.
        let nav = UINavigationBarAppearance()
        nav.configureWithTransparentBackground()
        UINavigationBar.appearance().standardAppearance = nav
        UINavigationBar.appearance().scrollEdgeAppearance = nav
        UINavigationBar.appearance().compactAppearance = nav

        UITableView.appearance().backgroundColor = .clear
        UITableViewCell.appearance().backgroundColor = .clear
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(biometric)
                .environmentObject(theme)
                .tint(theme.current.accentColor)
                .preferredColorScheme(.dark)
                .onReceive(NotificationCenter.default.publisher(for: UIApplication.didEnterBackgroundNotification)) { _ in
                    biometric.lockIfRequired()
                }
                .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
                    if biometric.requireUnlock {
                        Task { await biometric.unlock() }
                    }
                }
        }
    }
}

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

struct ContentView: View {
    @EnvironmentObject var theme: ThemeManager

    var body: some View {
        TabView {
            NotepadView()
                .tabItem { Label("Notepad", systemImage: "note.text") }
            KeysView()
                .tabItem { Label("Keys", systemImage: "key.fill") }
            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
        }
        // Gradient sits behind all tab content. Nav bar and form backgrounds are
        // cleared via UIAppearance in init() above so the gradient shows through.
        .background(
            LinearGradient(
                colors: theme.current.gradientColors,
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
        )
    }
}

struct BrandMark: View {
    @EnvironmentObject var theme: ThemeManager

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "lock.shield.fill")
                .font(.system(size: 18, weight: .semibold))
                .foregroundStyle(theme.current.accentColor)
            Text("NatePad")
                .font(.system(size: 18, weight: .semibold))
        }
    }
}

struct LockScreen: View {
    @EnvironmentObject var biometric: BiometricGate
    @EnvironmentObject var theme: ThemeManager

    var body: some View {
        ZStack {
            LinearGradient(
                colors: theme.current.gradientColors,
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 24) {
                Spacer()
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 64))
                    .foregroundStyle(theme.current.accentColor.gradient)
                Text("NatePad is locked")
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
}
