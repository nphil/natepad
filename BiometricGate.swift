import Foundation
import LocalAuthentication

@MainActor
final class BiometricGate: ObservableObject {
    @Published var unlocked: Bool = false
    @Published var requireUnlock: Bool {
        didSet { UserDefaults.standard.set(requireUnlock, forKey: Self.prefKey) }
    }

    private static let prefKey = "natepad.requireBiometric"

    init() {
        self.requireUnlock = UserDefaults.standard.bool(forKey: Self.prefKey)
    }

    func attemptUnlockOnLaunch() async {
        if requireUnlock {
            await unlock()
        } else {
            unlocked = true
        }
    }

    func unlock() async {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            unlocked = true
            return
        }
        do {
            let ok = try await context.evaluatePolicy(.deviceOwnerAuthentication,
                                                     localizedReason: "Unlock NatePad to access your PGP keys")
            if ok { unlocked = true }
        } catch {
            // User cancelled or auth failed — stay locked.
        }
    }

    /// Called when the app moves to background. Locks if biometrics are required.
    func lockIfRequired() {
        if requireUnlock { unlocked = false }
    }
}
