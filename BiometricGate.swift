import Foundation
import LocalAuthentication

/// Optional Face ID / Touch ID gate before showing key material.
/// Setting persists in UserDefaults so the user can opt in.
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

    /// Try to unlock with biometrics on app launch (silent if not enabled).
    func attemptUnlockOnLaunch() async {
        if requireUnlock {
            await unlock()
        } else {
            unlocked = true
        }
    }

    /// Prompt for biometric unlock.
    func unlock() async {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            // Device has no passcode set — fall back to unlocking. Edge case.
            unlocked = true
            return
        }
        do {
            let ok = try await context.evaluatePolicy(.deviceOwnerAuthentication,
                                                     localizedReason: "Unlock Natepad to access your PGP keys")
            if ok { unlocked = true }
        } catch {
            // User cancelled or auth failed; stay locked.
        }
    }

    func lock() {
        unlocked = false
    }
}
