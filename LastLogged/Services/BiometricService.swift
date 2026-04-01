import Foundation
import LocalAuthentication

@Observable
final class BiometricService {
    static let shared = BiometricService()

    enum BiometricType {
        case faceID
        case touchID
        case none
    }

    private(set) var isUnlocked = false

    var isEnabled: Bool {
        get { UserDefaults.standard.bool(forKey: "biometricLockEnabled") }
        set { UserDefaults.standard.set(newValue, forKey: "biometricLockEnabled") }
    }

    var availableBiometricType: BiometricType {
        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            return .none
        }
        switch context.biometryType {
        case .faceID: return .faceID
        case .touchID: return .touchID
        case .opticID: return .faceID // Treat opticID like faceID for UI purposes
        @unknown default: return .none
        }
    }

    var biometricName: String {
        switch availableBiometricType {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        case .none: return "Biometrics"
        }
    }

    var biometricIcon: String {
        switch availableBiometricType {
        case .faceID: return "faceid"
        case .touchID: return "touchid"
        case .none: return "lock.fill"
        }
    }

    /// Whether the app should show the lock screen
    var shouldShowLockScreen: Bool {
        isEnabled && !isUnlocked
    }

    private init() {
        // Start unlocked if biometric is not enabled
        isUnlocked = !UserDefaults.standard.bool(forKey: "biometricLockEnabled")
    }

    /// Authenticate with biometrics or device passcode
    @MainActor
    func authenticate() async -> Bool {
        let context = LAContext()
        context.localizedFallbackTitle = "Use Passcode"

        do {
            let success = try await context.evaluatePolicy(
                .deviceOwnerAuthentication, // Falls back to device passcode
                localizedReason: "Unlock Last Logged to access your data"
            )
            if success {
                isUnlocked = true
            }
            return success
        } catch {
            return false
        }
    }

    /// Lock the app (called when going to background)
    func lock() {
        guard isEnabled else { return }
        isUnlocked = false
    }

    /// Called when the setting is toggled — authenticate first to enable
    @MainActor
    func toggleEnabled() async -> Bool {
        if isEnabled {
            // Disabling — no auth needed
            isEnabled = false
            isUnlocked = true
            return true
        } else {
            // Enabling — authenticate first to confirm identity
            let success = await authenticate()
            if success {
                isEnabled = true
            }
            return success
        }
    }
}
