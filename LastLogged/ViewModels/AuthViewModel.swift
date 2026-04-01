import Foundation
import SwiftData
import AuthenticationServices
import CryptoKit

@Observable
final class AuthViewModel {
    // MARK: - State

    var email = ""
    var password = ""
    var isSignUp = false
    var isLoading = false
    var errorMessage: String?
    var showingEmailForm = false
    var showingForgotPassword = false
    var resetPasswordSent = false

    private let modelContext: ModelContext
    private var currentNonce: String?

    // MARK: - Rate Limiting

    private static let maxFailedAttempts = 5
    private static let cooldownDuration: TimeInterval = 30

    private var failedAttemptCount = 0
    private var cooldownExpiresAt: Date?
    private var cooldownTimer: Task<Void, Never>?

    var cooldownSecondsRemaining: Int {
        guard let expiresAt = cooldownExpiresAt else { return 0 }
        return max(0, Int(expiresAt.timeIntervalSinceNow.rounded(.up)))
    }

    var isLockedOut: Bool {
        cooldownSecondsRemaining > 0
    }

    // MARK: - Computed

    var isSignedIn: Bool {
        SupabaseService.shared.isSignedIn
    }

    var userEmail: String? {
        SupabaseService.shared.currentUserEmail
    }

    var isEmailValid: Bool {
        let trimmed = email.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return false }
        let emailRegex = #"^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$"#
        return trimmed.range(of: emailRegex, options: .regularExpression) != nil
    }

    var emailValidationMessage: String? {
        let trimmed = email.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return nil }
        return isEmailValid ? nil : "Enter a valid email address"
    }

    var isFormValid: Bool {
        isEmailValid && isPasswordValid
    }

    var isPasswordValid: Bool {
        password.count >= 8
            && password.range(of: "[A-Z]", options: .regularExpression) != nil
            && password.range(of: "[a-z]", options: .regularExpression) != nil
            && password.range(of: "[0-9]", options: .regularExpression) != nil
    }

    var passwordValidationMessages: [String] {
        guard isSignUp else { return [] }
        var messages: [String] = []
        if password.count < 8 { messages.append("At least 8 characters") }
        if password.range(of: "[A-Z]", options: .regularExpression) == nil { messages.append("One uppercase letter") }
        if password.range(of: "[a-z]", options: .regularExpression) == nil { messages.append("One lowercase letter") }
        if password.range(of: "[0-9]", options: .regularExpression) == nil { messages.append("One number") }
        return messages
    }

    // MARK: - Initialization

    init(modelContext: ModelContext) {
        self.modelContext = modelContext
    }

    // MARK: - Apple Sign In

    func configureAppleSignInRequest(_ request: ASAuthorizationAppleIDRequest) {
        let nonce = randomNonceString()
        currentNonce = nonce
        request.requestedScopes = [.fullName, .email]
        request.nonce = sha256(nonce)
    }

    func handleAppleSignInCompletion(_ result: Result<ASAuthorization, Error>) {
        switch result {
        case .success(let authorization):
            guard let appleIDCredential = authorization.credential as? ASAuthorizationAppleIDCredential,
                  let identityTokenData = appleIDCredential.identityToken,
                  let idToken = String(data: identityTokenData, encoding: .utf8),
                  let nonce = currentNonce else {
                errorMessage = "Failed to get Apple ID credentials."
                return
            }

            isLoading = true
            errorMessage = nil

            Task { @MainActor in
                do {
                    try await SupabaseService.shared.signInWithApple(idToken: idToken, nonce: nonce)
                    SupabaseService.shared.syncOnForeground(modelContext: modelContext)
                } catch {
                    errorMessage = error.localizedDescription
                }
                isLoading = false
            }

        case .failure(let error):
            // ASAuthorizationError.canceled means user dismissed — not an error
            if (error as? ASAuthorizationError)?.code != .canceled {
                errorMessage = error.localizedDescription
            }
        }
    }

    // MARK: - Email/Password

    func submitEmailForm() {
        guard isFormValid else { return }
        guard !isLockedOut else {
            errorMessage = "Too many attempts. Wait \(cooldownSecondsRemaining)s."
            return
        }

        isLoading = true
        errorMessage = nil

        let trimmedEmail = email.trimmingCharacters(in: .whitespaces)

        Task { @MainActor in
            do {
                if isSignUp {
                    try await SupabaseService.shared.signUpEmail(
                        email: trimmedEmail, password: password
                    )
                } else {
                    try await SupabaseService.shared.signInEmail(
                        email: trimmedEmail, password: password
                    )
                }
                // Success — reset rate limiting
                failedAttemptCount = 0
                cooldownExpiresAt = nil
                cooldownTimer?.cancel()
                SupabaseService.shared.syncOnForeground(modelContext: modelContext)
                email = ""
                password = ""
            } catch {
                failedAttemptCount += 1
                if failedAttemptCount >= Self.maxFailedAttempts {
                    startCooldown()
                    errorMessage = "Too many failed attempts. Please wait \(Int(Self.cooldownDuration)) seconds."
                } else {
                    let remaining = Self.maxFailedAttempts - failedAttemptCount
                    let friendlyMessage = Self.sanitizeAuthError(error)
                    errorMessage = "\(friendlyMessage) (\(remaining) attempt\(remaining == 1 ? "" : "s") remaining)"
                }
            }
            isLoading = false
        }
    }

    // MARK: - Password Reset

    func resetPassword() {
        let trimmedEmail = email.trimmingCharacters(in: .whitespaces)
        guard !trimmedEmail.isEmpty else {
            errorMessage = "Please enter your email address."
            return
        }
        guard !isLockedOut else {
            errorMessage = "Too many attempts. Wait \(cooldownSecondsRemaining)s."
            return
        }

        isLoading = true
        errorMessage = nil

        Task { @MainActor in
            do {
                try await SupabaseService.shared.resetPassword(email: trimmedEmail)
                resetPasswordSent = true
            } catch {
                failedAttemptCount += 1
                if failedAttemptCount >= Self.maxFailedAttempts {
                    startCooldown()
                    errorMessage = "Too many attempts. Please wait \(Int(Self.cooldownDuration)) seconds."
                } else {
                    errorMessage = "Unable to send reset link. Please check your email and try again."
                }
            }
            isLoading = false
        }
    }

    // MARK: - Cooldown

    private func startCooldown() {
        cooldownExpiresAt = Date().addingTimeInterval(Self.cooldownDuration)
        cooldownTimer?.cancel()
        cooldownTimer = Task { @MainActor in
            while cooldownSecondsRemaining > 0 {
                try? await Task.sleep(for: .seconds(1))
                // Force observation update by touching the property
                _ = cooldownSecondsRemaining
            }
            // Cooldown expired — reset
            failedAttemptCount = 0
            cooldownExpiresAt = nil
        }
    }

    // MARK: - Sign Out

    func signOut() {
        isLoading = true
        errorMessage = nil

        Task { @MainActor in
            do {
                try await SupabaseService.shared.signOut()
            } catch {
                errorMessage = error.localizedDescription
            }
            isLoading = false
        }
    }

    // MARK: - Nonce Helpers

    private func randomNonceString(length: Int = 32) -> String {
        precondition(length > 0)
        var randomBytes = [UInt8](repeating: 0, count: length)
        let errorCode = SecRandomCopyBytes(kSecRandomDefault, randomBytes.count, &randomBytes)
        if errorCode != errSecSuccess {
            fatalError("Unable to generate nonce. SecRandomCopyBytes failed with OSStatus \(errorCode)")
        }
        let charset: [Character] = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        return String(randomBytes.map { charset[Int($0) % charset.count] })
    }

    private func sha256(_ input: String) -> String {
        let inputData = Data(input.utf8)
        let hashed = SHA256.hash(data: inputData)
        return hashed.compactMap { String(format: "%02x", $0) }.joined()
    }

    // MARK: - Error Sanitization

    static func sanitizeAuthError(_ error: Error) -> String {
        let message = error.localizedDescription.lowercased()

        if message.contains("invalid login credentials") || message.contains("invalid_credentials") {
            return "Incorrect email or password. Please try again."
        }
        if message.contains("email not confirmed") {
            return "Please confirm your email address before signing in. Check your inbox."
        }
        if message.contains("user already registered") || message.contains("already been registered") {
            return "An account with this email already exists. Try signing in instead."
        }
        if message.contains("email rate limit") || message.contains("rate limit") {
            return "Too many requests. Please wait a moment and try again."
        }
        if message.contains("network") || message.contains("connection") || message.contains("offline") {
            return "Network error. Please check your connection and try again."
        }
        if message.contains("weak password") || message.contains("password") {
            return "Password does not meet requirements. Please use a stronger password."
        }
        if message.contains("signup is disabled") {
            return "Sign up is currently unavailable. Please try again later."
        }

        // Generic fallback — never expose raw error
        return "Something went wrong. Please try again."
    }
}
