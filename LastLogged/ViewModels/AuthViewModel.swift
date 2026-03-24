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

    private let modelContext: ModelContext
    private var currentNonce: String?

    // MARK: - Computed

    var isSignedIn: Bool {
        SupabaseService.shared.isSignedIn
    }

    var userEmail: String? {
        SupabaseService.shared.currentUserEmail
    }

    var isFormValid: Bool {
        !email.trimmingCharacters(in: .whitespaces).isEmpty
            && password.count >= 6
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
                SupabaseService.shared.syncOnForeground(modelContext: modelContext)
                email = ""
                password = ""
            } catch {
                errorMessage = error.localizedDescription
            }
            isLoading = false
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
}
