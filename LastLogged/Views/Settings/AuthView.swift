import SwiftUI
import SwiftData
import AuthenticationServices

struct AuthView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: AuthViewModel?

    var body: some View {
        NavigationStack {
            Group {
                if let viewModel {
                    if viewModel.isSignedIn {
                        signedInView(viewModel: viewModel)
                    } else {
                        signInView(viewModel: viewModel)
                    }
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Account")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .onAppear {
            if viewModel == nil {
                viewModel = AuthViewModel(modelContext: modelContext)
            }
        }
    }

    // MARK: - Signed In View

    private func signedInView(viewModel: AuthViewModel) -> some View {
        Form {
            Section {
                HStack {
                    Image(systemName: "person.crop.circle.fill")
                        .font(.largeTitle)
                        .foregroundStyle(.accent)
                    VStack(alignment: .leading) {
                        Text("Signed In")
                            .font(.headline)
                        if let email = viewModel.userEmail {
                            Text(email)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }

            Section {
                Text("Your data is being synced to the cloud. You can access it on any device by signing in with the same account.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            Section {
                Button("Sign Out", role: .destructive) {
                    viewModel.signOut()
                }
                .disabled(viewModel.isLoading)
            } footer: {
                Text("Signing out will stop cloud sync, but your local data will be preserved.")
            }

            if let error = viewModel.errorMessage {
                Section {
                    Text(error)
                        .foregroundStyle(.red)
                        .font(.caption)
                }
            }
        }
    }

    // MARK: - Sign In View

    private func signInView(viewModel: AuthViewModel) -> some View {
        ScrollView {
            if viewModel.showingForgotPassword {
                forgotPasswordView(viewModel: viewModel)
            } else {
            VStack(spacing: 24) {
                // Header
                VStack(spacing: 8) {
                    Image(systemName: "icloud.fill")
                        .font(.system(size: 48))
                        .foregroundStyle(.accent)
                    Text("Sign In to Sync")
                        .font(.title2.bold())
                    Text("Back up your data and access it across all your devices. Signing in is optional.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                .padding(.top, 24)
                .padding(.horizontal)

                // Apple Sign In
                SignInWithAppleButton(.signIn) { request in
                    viewModel.configureAppleSignInRequest(request)
                } onCompletion: { result in
                    viewModel.handleAppleSignInCompletion(result)
                }
                .signInWithAppleButtonStyle(.black)
                .frame(height: 50)
                .cornerRadius(10)
                .padding(.horizontal)
                .accessibilityLabel("Sign in with Apple")
                .accessibilityHint("Use your Apple ID to sign in")

                // Divider
                HStack {
                    Rectangle().fill(.separator).frame(height: 1)
                    Text("or")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Rectangle().fill(.separator).frame(height: 1)
                }
                .padding(.horizontal)

                // Email toggle
                if viewModel.showingEmailForm {
                    emailFormView(viewModel: viewModel)
                } else {
                    Button {
                        withAnimation {
                            viewModel.showingEmailForm = true
                        }
                    } label: {
                        HStack {
                            Image(systemName: "envelope.fill")
                            Text("Continue with Email")
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(.fill.tertiary)
                        .cornerRadius(10)
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal)
                    .accessibilityLabel("Continue with email")
                    .accessibilityHint("Sign in or create an account using email")
                }

                // Error
                if let error = viewModel.errorMessage {
                    Text(error)
                        .foregroundStyle(.red)
                        .font(.caption)
                        .padding(.horizontal)
                }

                if viewModel.isLoading {
                    ProgressView()
                }

                Spacer()
            }
            } // end else (not forgotPassword)
        }
    }

    // MARK: - Email Form

    private func emailFormView(viewModel: AuthViewModel) -> some View {
        VStack(spacing: 16) {
            // Sign up / Sign in toggle
            Picker("Mode", selection: $viewModel.isSignUp) {
                Text("Sign In").tag(false)
                Text("Sign Up").tag(true)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)

            TextField("Email", text: $viewModel.email)
                .textContentType(.emailAddress)
                .keyboardType(.emailAddress)
                .autocapitalization(.none)
                .textFieldStyle(.roundedBorder)
                .padding(.horizontal)

            // Inline email validation
            if let emailError = viewModel.emailValidationMessage {
                HStack(spacing: 6) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.caption2)
                        .foregroundStyle(.red)
                    Text(emailError)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal)
            }

            SecureField("Password", text: $viewModel.password)
                .textContentType(viewModel.isSignUp ? .newPassword : .password)
                .textFieldStyle(.roundedBorder)
                .padding(.horizontal)

            // Inline password validation (sign-up only)
            if viewModel.isSignUp && !viewModel.password.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(viewModel.passwordValidationMessages, id: \.self) { message in
                        HStack(spacing: 6) {
                            Image(systemName: "xmark.circle.fill")
                                .font(.caption2)
                                .foregroundStyle(.red)
                            Text(message)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    if viewModel.passwordValidationMessages.isEmpty {
                        HStack(spacing: 6) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.caption2)
                                .foregroundStyle(.green)
                            Text("Password meets requirements")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .padding(.horizontal)
            }

            // Cooldown timer
            if viewModel.isLockedOut {
                HStack(spacing: 8) {
                    Image(systemName: "lock.fill")
                        .foregroundStyle(.orange)
                    Text("Try again in \(viewModel.cooldownSecondsRemaining)s")
                        .font(.subheadline)
                        .foregroundStyle(.orange)
                }
                .padding(.horizontal)
            }

            Button {
                viewModel.submitEmailForm()
            } label: {
                Text(viewModel.isSignUp ? "Create Account" : "Sign In")
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!viewModel.isFormValid || viewModel.isLoading || viewModel.isLockedOut)
            .padding(.horizontal)
            .accessibilityLabel(viewModel.isSignUp ? "Create account" : "Sign in")
            .accessibilityHint(viewModel.isSignUp ? "Create a new account with your email" : "Sign in with your email and password")

            // Forgot Password (sign-in only)
            if !viewModel.isSignUp {
                Button {
                    withAnimation {
                        viewModel.showingForgotPassword = true
                    }
                } label: {
                    Text("Forgot Password?")
                        .font(.subheadline)
                        .foregroundStyle(.accent)
                }
                .padding(.top, 4)
                .accessibilityLabel("Forgot password")
                .accessibilityHint("Send a password reset link to your email")
            }
        }
    }

    // MARK: - Forgot Password View

    private func forgotPasswordView(viewModel: AuthViewModel) -> some View {
        VStack(spacing: 20) {
            if viewModel.resetPasswordSent {
                // Success state
                Image(systemName: "envelope.badge.fill")
                    .font(.system(size: 48))
                    .foregroundStyle(.green)
                    .padding(.top, 24)

                Text("Check Your Email")
                    .font(.title2.bold())

                Text("We've sent a password reset link to your email address. Follow the link to reset your password.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)

                Button {
                    withAnimation {
                        viewModel.showingForgotPassword = false
                        viewModel.resetPasswordSent = false
                    }
                } label: {
                    Text("Back to Sign In")
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                }
                .buttonStyle(.borderedProminent)
                .padding(.horizontal)
            } else {
                // Input state
                Image(systemName: "lock.rotation")
                    .font(.system(size: 48))
                    .foregroundStyle(.accent)
                    .padding(.top, 24)

                Text("Reset Password")
                    .font(.title2.bold())

                Text("Enter your email address and we'll send you a link to reset your password.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)

                TextField("Email", text: $viewModel.email)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .autocapitalization(.none)
                    .textFieldStyle(.roundedBorder)
                    .padding(.horizontal)

                if viewModel.isLockedOut {
                    HStack(spacing: 8) {
                        Image(systemName: "lock.fill")
                            .foregroundStyle(.orange)
                        Text("Try again in \(viewModel.cooldownSecondsRemaining)s")
                            .font(.subheadline)
                            .foregroundStyle(.orange)
                    }
                }

                Button {
                    viewModel.resetPassword()
                } label: {
                    Text("Send Reset Link")
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.email.trimmingCharacters(in: .whitespaces).isEmpty || viewModel.isLoading || viewModel.isLockedOut)
                .padding(.horizontal)
                .accessibilityLabel("Send reset link")
                .accessibilityHint("Send a password reset email")

                Button {
                    withAnimation {
                        viewModel.showingForgotPassword = false
                    }
                } label: {
                    Text("Back to Sign In")
                        .font(.subheadline)
                        .foregroundStyle(.accent)
                }

                if viewModel.isLoading {
                    ProgressView()
                }
            }
        }
    }
}

#Preview {
    AuthView()
        .modelContainer(for: [TrackerItem.self, CompletionLog.self, TrackerCategory.self], inMemory: true)
}
