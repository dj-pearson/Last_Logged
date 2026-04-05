import SwiftUI
import SwiftData
import UIKit

struct HomeView: View {
    @Environment(\.modelContext) private var modelContext
    @State private var viewModel: HomeViewModel?
    @State private var showingAddTracker = false
    @State private var showingTemplatePicker = false
    @State private var showingPaywall = false
    @State private var editingItem: TrackerItem?
    @State private var itemToArchive: TrackerItem?
    @State private var showArchiveConfirmation = false
    @State private var toastInfo: HomeViewModel.LogUndoInfo?
    @State private var toastDismissTask: Task<Void, Never>?
    @State private var showingSettings = false
    @State private var showingWidgetPrompt = false
    @AppStorage("widgetPromptDismissed") private var widgetPromptDismissed = false

    var body: some View {
        NavigationStack {
            Group {
                if let viewModel {
                    if viewModel.trackerItems.isEmpty {
                        emptyStateView
                    } else {
                        trackerListView(viewModel: viewModel)
                    }
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Last Logged")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        showingSettings = true
                    } label: {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("Settings")
                    .accessibilityHint("Open app settings")
                }
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        Button {
                            if viewModel?.canCreateTracker == true {
                                showingAddTracker = true
                            } else {
                                showingPaywall = true
                            }
                        } label: {
                            Label("New Tracker", systemImage: "plus")
                        }
                        Button {
                            if viewModel?.canCreateTracker == true {
                                showingTemplatePicker = true
                            } else {
                                showingPaywall = true
                            }
                        } label: {
                            Label("Add from Templates", systemImage: "list.bullet.rectangle")
                        }
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Add tracker")
                    .accessibilityHint("Create a new tracker or add from templates")
                }
            }
        }
        .onAppear {
            if viewModel == nil {
                viewModel = HomeViewModel(modelContext: modelContext)
            }
        }
        .sheet(isPresented: $showingAddTracker) {
            if let viewModel {
                AddTrackerView(viewModel: viewModel)
            }
        }
        .sheet(item: $editingItem) { item in
            if let viewModel {
                AddTrackerView(viewModel: viewModel, editingItem: item)
            }
        }
        .sheet(isPresented: $showingTemplatePicker) {
            if let viewModel {
                TemplatePickerView(viewModel: viewModel)
            }
        }
        .sheet(isPresented: $showingPaywall) {
            PaywallView()
        }
        .sheet(isPresented: $showingSettings) {
            SettingsView()
        }
        .sheet(isPresented: $showingWidgetPrompt) {
            WidgetPromptView()
        }
        .confirmationDialog("Archive Tracker?", isPresented: $showArchiveConfirmation, presenting: itemToArchive) { item in
            Button("Archive", role: .destructive) {
                viewModel?.archiveItem(item)
            }
        } message: { item in
            Text("'\(item.name)' will be hidden from your home list.")
        }
        .overlay(alignment: .top) {
            if SupabaseService.shared.sessionExpired {
                sessionExpiredBanner
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .overlay(alignment: .bottom) {
            if let error = viewModel?.lastError {
                errorBanner(error)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            } else if toastInfo != nil {
                toastView
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.3), value: toastInfo != nil)
        .onChange(of: viewModel?.trackerItems.count) { oldCount, newCount in
            if let newCount, newCount >= 3, !widgetPromptDismissed {
                // Small delay so the add-tracker sheet dismisses first
                Task { @MainActor in
                    try? await Task.sleep(for: .seconds(0.5))
                    showingWidgetPrompt = true
                }
            }
        }
    }

    // MARK: - Empty State

    private var emptyStateView: some View {
        VStack(spacing: 20) {
            ZStack {
                Circle()
                    .fill(Color.accentColor.opacity(0.15))
                    .frame(width: 120, height: 120)
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 54, weight: .semibold))
                    .foregroundStyle(Color.accentColor)
                    .accessibilityHidden(true)
            }
            .padding(.bottom, 4)

            Text("Start Tracking What Matters")
                .font(.title2.bold())
                .multilineTextAlignment(.center)

            Text("Add your first tracker and never forget the small things again.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            VStack(spacing: 10) {
                Button {
                    showingAddTracker = true
                } label: {
                    Label("Add Your First Tracker", systemImage: "plus.circle.fill")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .accessibilityHint("Create a custom tracker")

                Button {
                    showingTemplatePicker = true
                } label: {
                    Label("Browse Templates", systemImage: "list.bullet.rectangle")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .controlSize(.large)
                .accessibilityHint("Choose from pre-built tracker templates")
            }
            .padding(.horizontal, 40)
            .padding(.top, 8)
        }
        .padding()
        .accessibilityElement(children: .contain)
    }

    // MARK: - Tracker List

    private func trackerListView(viewModel: HomeViewModel) -> some View {
        List {
            ForEach(viewModel.itemsByCategory, id: \.category.id) { group in
                Section {
                    ForEach(group.items, id: \.id) { item in
                        NavigationLink {
                            TrackerDetailView(item: item)
                        } label: {
                            TrackerRowView(item: item) {
                                logItem(item)
                            }
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button(role: .destructive) {
                                itemToArchive = item
                                showArchiveConfirmation = true
                            } label: {
                                Label("Archive", systemImage: "archivebox")
                            }
                        }
                        .contextMenu {
                            Button {
                                editingItem = item
                            } label: {
                                Label("Edit", systemImage: "pencil")
                            }
                            Button(role: .destructive) {
                                itemToArchive = item
                                showArchiveConfirmation = true
                            } label: {
                                Label("Archive", systemImage: "archivebox")
                            }
                        }
                    }
                } header: {
                    Label(group.category.name, systemImage: group.category.iconName)
                        .font(.headline)
                        .foregroundStyle(Color(hex: group.category.colorHex) ?? .primary)
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    // MARK: - Log & Undo

    private func logItem(_ item: TrackerItem) {
        guard let viewModel else { return }
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.impactOccurred()
        let info = viewModel.logCompletion(for: item)
        showToast(info: info)

        // Start a Live Activity / Dynamic Island acknowledgement for a
        // premium confirmation cue (iOS 16.1+, gracefully no-ops on older).
        if #available(iOS 16.1, *) {
            LiveActivityManager.startLogActivity(
                trackerName: item.name,
                iconName: item.iconName
            )
        }
    }

    private func showToast(info: HomeViewModel.LogUndoInfo) {
        toastDismissTask?.cancel()
        withAnimation {
            toastInfo = info
        }
        toastDismissTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(4))
            if !Task.isCancelled {
                dismissToast()
            }
        }
    }

    private func dismissToast() {
        withAnimation {
            toastInfo = nil
        }
        toastDismissTask?.cancel()
        toastDismissTask = nil
    }

    // MARK: - Error Banner

    private func errorBanner(_ message: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(.white)
                .accessibilityHidden(true)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(.white)
            Spacer()
            Button {
                viewModel?.lastError = nil
            } label: {
                Image(systemName: "xmark")
                    .foregroundStyle(.white.opacity(0.8))
            }
            .accessibilityLabel("Dismiss error")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.red, in: RoundedRectangle(cornerRadius: 12))
        .shadow(radius: 4, y: 2)
        .padding(.horizontal, 16)
        .padding(.bottom, 16)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Error: \(message)")
    }

    // MARK: - Session Expired Banner

    private var sessionExpiredBanner: some View {
        HStack(spacing: 10) {
            Image(systemName: "exclamationmark.shield.fill")
                .foregroundStyle(.white)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text("Session Expired")
                    .font(.subheadline.bold())
                    .foregroundStyle(.white)
                Text("Please sign in again to resume syncing.")
                    .font(.caption)
                    .foregroundStyle(.white.opacity(0.9))
            }
            Spacer()
            Button {
                SupabaseService.shared.clearSessionExpiredBanner()
                showingSettings = true
            } label: {
                Text("Sign In")
                    .font(.subheadline.bold())
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(.white.opacity(0.25), in: RoundedRectangle(cornerRadius: 8))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.orange, in: RoundedRectangle(cornerRadius: 12))
        .shadow(radius: 4, y: 2)
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Your session has expired. Tap Sign In to re-authenticate.")
    }

    // MARK: - Toast View

    private var toastView: some View {
        HStack(spacing: 12) {
            Text("Logged!")
                .fontWeight(.medium)
                .accessibilityHidden(true)
            Button("Undo") {
                if let toastInfo {
                    viewModel?.undoLog(toastInfo)
                }
                dismissToast()
            }
            .fontWeight(.semibold)
            .accessibilityLabel("Undo log")
            .accessibilityHint("Double-tap to undo the last log entry")
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
        .shadow(radius: 4, y: 2)
        .padding(.bottom, 16)
        .onAppear {
            UIAccessibility.post(notification: .announcement, argument: "Completion logged")
        }
    }
}

// MARK: - Tracker Row

struct TrackerRowView: View {
    let item: TrackerItem
    let onLog: () -> Void
    @State private var isLogging = false

    private static let relativeFormatter: RelativeDateTimeFormatter = {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        return formatter
    }()

    var body: some View {
        HStack {
            Image(systemName: item.iconName)
                .foregroundStyle(.accent)
                .frame(width: 28)
                .accessibilityHidden(true)

            Text(item.name)

            Spacer()

            Text(elapsedTimeText)
                .foregroundStyle(urgencyColor)
                .font(.subheadline)

            Button {
                guard !isLogging else { return }
                isLogging = true
                onLog()
                // Brief visual feedback before resetting
                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(400))
                    isLogging = false
                }
            } label: {
                if isLogging {
                    ProgressView()
                        .font(.title2)
                } else {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.title2)
                        .foregroundStyle(.green)
                }
            }
            .buttonStyle(.plain)
            .disabled(isLogging)
            .frame(minWidth: 44, minHeight: 44)
            .accessibilityLabel("Log \(item.name)")
            .accessibilityHint("Double-tap to log completion now")
        }
        .accessibilityElement(children: .combine)
    }

    private var elapsedTimeText: String {
        guard let lastCompleted = item.lastCompletedAt else {
            return "Never logged"
        }
        return Self.relativeFormatter.localizedString(for: lastCompleted, relativeTo: Date())
    }

    private var urgencyColor: Color {
        guard let lastCompleted = item.lastCompletedAt else {
            return item.reminderIntervalDays != nil ? .red : .secondary
        }
        guard let interval = item.reminderIntervalDays, interval > 0 else {
            return .secondary
        }
        let elapsed = Date().timeIntervalSince(lastCompleted)
        let intervalSeconds = Double(interval) * 86400
        let ratio = elapsed / intervalSeconds

        if ratio > 1.0 {
            return .red
        } else if ratio >= 0.75 {
            return .yellow
        } else {
            return .green
        }
    }
}

// MARK: - Color from Hex

extension Color {
    init?(hex: String) {
        var hexSanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        hexSanitized = hexSanitized.replacingOccurrences(of: "#", with: "")

        guard hexSanitized.count == 6 else { return nil }

        var rgb: UInt64 = 0
        guard Scanner(string: hexSanitized).scanHexInt64(&rgb) else { return nil }

        self.init(
            red: Double((rgb & 0xFF0000) >> 16) / 255.0,
            green: Double((rgb & 0x00FF00) >> 8) / 255.0,
            blue: Double(rgb & 0x0000FF) / 255.0
        )
    }
}

#Preview {
    HomeView()
        .modelContainer(for: [TrackerItem.self, CompletionLog.self, TrackerCategory.self], inMemory: true)
}
