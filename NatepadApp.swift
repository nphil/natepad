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
        UIScrollView.appearance().backgroundColor = .clear
        UICollectionView.appearance().backgroundColor = .clear
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(biometric)
                .environmentObject(theme)
                .tint(theme.current.accentColor)
                .preferredColorScheme(theme.current.preferredColorScheme)
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
        // ThemeBackground is applied inside each tab's NavigationStack so it renders
        // within the correct layer — putting it behind TabView doesn't work because
        // NavigationStack renders on an opaque system surface that sits in front.
        TabView {
            NotepadView()
                .tabItem { Label("Notepad", systemImage: "note.text") }
            KeysView()
                .tabItem { Label("Keys", systemImage: "key.fill") }
            SettingsView()
                .tabItem { Label("Settings", systemImage: "gearshape.fill") }
        }
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
            theme.current.backgroundColor.ignoresSafeArea()

            VStack(spacing: 24) {
                Spacer()
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 64))
                    .foregroundStyle(theme.current.accentColor.gradient)
                Text("NatePad is locked")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(theme.current.foregroundColor)
                Text("Authenticate to access your keys")
                    .foregroundStyle(theme.current.foregroundColor.opacity(0.7))
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
