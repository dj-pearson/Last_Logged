import SwiftUI
import SwiftData

struct SettingsView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @State private var viewModel: SettingsViewModel?

    var body: some View {
        NavigationStack {
            Group {
                if let viewModel {
                    settingsForm(viewModel: viewModel)
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .onAppear {
            if viewModel == nil {
                viewModel = SettingsViewModel(modelContext: modelContext)
            }
        }
        .sheet(isPresented: Binding(
            get: { viewModel?.showingAuth ?? false },
            set: { viewModel?.showingAuth = $0 }
        )) {
            AuthView()
        }
        .sheet(isPresented: Binding(
            get: { viewModel?.showingPaywall ?? false },
            set: { viewModel?.showingPaywall = $0 }
        )) {
            PaywallView()
        }
    }

    // MARK: - Settings Form

    private func settingsForm(viewModel: SettingsViewModel) -> some View {
        Form {
            accountSection(viewModel: viewModel)
            notificationsSection(viewModel: viewModel)
            dataSection(viewModel: viewModel)
            aboutSection(viewModel: viewModel)
        }
    }

    // MARK: - Account Section

    private func accountSection(viewModel: SettingsViewModel) -> some View {
        Section("Account") {
            if viewModel.isSignedIn {
                HStack {
                    Image(systemName: "person.crop.circle.fill")
                        .font(.title2)
                        .foregroundStyle(.accent)
                    VStack(alignment: .leading) {
                        Text("Signed In")
                            .font(.subheadline.bold())
                        if let email = viewModel.userEmail {
                            Text(email)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                HStack {
                    Text("Subscription")
                    Spacer()
                    Text(viewModel.subscriptionTierLabel)
                        .foregroundStyle(.secondary)
                }

                if !viewModel.isPremium {
                    Button {
                        viewModel.showingPaywall = true
                    } label: {
                        Label("Upgrade to Premium", systemImage: "star.fill")
                    }
                } else {
                    Button {
                        viewModel.showingPaywall = true
                    } label: {
                        Label("Manage Subscription", systemImage: "creditcard")
                    }
                }

                Button("Sign Out", role: .destructive) {
                    viewModel.signOut()
                }
            } else {
                Button {
                    viewModel.showingAuth = true
                } label: {
                    Label("Sign In to Sync", systemImage: "person.crop.circle")
                }

                HStack {
                    Text("Subscription")
                    Spacer()
                    Text(viewModel.subscriptionTierLabel)
                        .foregroundStyle(.secondary)
                }

                if !viewModel.isPremium {
                    Button {
                        viewModel.showingPaywall = true
                    } label: {
                        Label("Upgrade to Premium", systemImage: "star.fill")
                    }
                }
            }
        }
    }

    // MARK: - Notifications Section

    private func notificationsSection(viewModel: SettingsViewModel) -> some View {
        Section("Notifications") {
            Toggle("Reminders", isOn: Binding(
                get: { viewModel.remindersEnabled },
                set: { viewModel.remindersEnabled = $0 }
            ))

            if viewModel.remindersEnabled {
                DatePicker(
                    "Default Reminder Time",
                    selection: Binding(
                        get: { viewModel.defaultReminderTime },
                        set: { viewModel.defaultReminderTime = $0 }
                    ),
                    displayedComponents: .hourAndMinute
                )
            }
        }
    }

    // MARK: - Data Section

    private func dataSection(viewModel: SettingsViewModel) -> some View {
        Section("Data") {
            ShareLink(
                item: viewModel.exportDataLocally(),
                subject: Text("Last Logged Export"),
                message: Text("My Last Logged data export")
            ) {
                Label("Export Data", systemImage: "square.and.arrow.up")
            }

            Button(role: .destructive) {
                viewModel.showClearDataConfirmation = true
            } label: {
                Label("Clear All Data", systemImage: "trash")
            }
            .confirmationDialog(
                "Clear All Data?",
                isPresented: Binding(
                    get: { viewModel.showClearDataConfirmation },
                    set: { viewModel.showClearDataConfirmation = $0 }
                ),
                titleVisibility: .visible
            ) {
                Button("Delete Everything", role: .destructive) {
                    viewModel.clearAllData()
                }
            } message: {
                Text("This will permanently delete all your trackers, categories, and completion history. This cannot be undone.")
            }
        }
    }

    // MARK: - About Section

    private func aboutSection(viewModel: SettingsViewModel) -> some View {
        Section("About") {
            HStack {
                Text("Version")
                Spacer()
                Text("\(viewModel.appVersion) (\(viewModel.buildNumber))")
                    .foregroundStyle(.secondary)
            }

            Link(destination: URL(string: "https://lastlogged.com/privacy")!) {
                Label("Privacy Policy", systemImage: "hand.raised")
            }

            Link(destination: URL(string: "https://lastlogged.com/terms")!) {
                Label("Terms of Service", systemImage: "doc.text")
            }

            Link(destination: URL(string: "mailto:support@lastlogged.com")!) {
                Label("Contact Support", systemImage: "envelope")
            }
        }
    }
}

#Preview {
    SettingsView()
        .modelContainer(for: [TrackerItem.self, CompletionLog.self, TrackerCategory.self], inMemory: true)
}
