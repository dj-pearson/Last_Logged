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

            SecureField("Password", text: $viewModel.password)
                .textContentType(viewModel.isSignUp ? .newPassword : .password)
                .textFieldStyle(.roundedBorder)
                .padding(.horizontal)

            Button {
                viewModel.submitEmailForm()
            } label: {
                Text(viewModel.isSignUp ? "Create Account" : "Sign In")
                    .frame(maxWidth: .infinity)
                    .frame(height: 50)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!viewModel.isFormValid || viewModel.isLoading)
            .padding(.horizontal)

            if viewModel.isSignUp {
                Text("Password must be at least 6 characters.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

#Preview {
    AuthView()
        .modelContainer(for: [TrackerItem.self, CompletionLog.self, TrackerCategory.self], inMemory: true)
}
