import SwiftUI
import SwiftData

struct TrackerDetailView: View {
    @Environment(\.modelContext) private var modelContext
    @State private var viewModel: TrackerDetailViewModel?
    @State private var editingItem: TrackerItem?
    @State private var showingPaywall = false

    let item: TrackerItem

    private static let relativeFormatter: RelativeDateTimeFormatter = {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        return formatter
    }()

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d, yyyy 'at' h:mm a"
        return formatter
    }()

    var body: some View {
        List {
            headerSection
            historySection
        }
        .listStyle(.insetGrouped)
        .navigationTitle(item.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    editingItem = item
                } label: {
                    Text("Edit")
                }
                .accessibilityLabel("Edit tracker")
                .accessibilityHint("Edit this tracker's details")
            }
        }
        .onAppear {
            if viewModel == nil {
                viewModel = TrackerDetailViewModel(modelContext: modelContext, item: item)
            } else {
                viewModel?.fetchCompletionLogs()
            }
        }
        .sheet(item: $editingItem) { _ in
            EditTrackerSheet(item: item, modelContext: modelContext) {
                viewModel?.fetchCompletionLogs()
            }
        }
        .sheet(isPresented: $showingPaywall) {
            PaywallView()
        }
    }

    // MARK: - Header

    private var headerSection: some View {
        Section {
            HStack {
                Image(systemName: item.iconName)
                    .font(.title2)
                    .foregroundStyle(.accent)
                    .frame(width: 36)

                VStack(alignment: .leading, spacing: 4) {
                    Text(item.name)
                        .font(.headline)

                    if let category = viewModel?.category {
                        Label(category.name, systemImage: category.iconName)
                            .font(.subheadline)
                            .foregroundStyle(Color(hex: category.colorHex) ?? .secondary)
                    }
                }

                Spacer()
            }

            if let interval = item.reminderIntervalDays {
                LabeledContent("Reminder Interval") {
                    Text(intervalText(days: interval))
                        .foregroundStyle(.secondary)
                }
            }

            LabeledContent("Last Completed") {
                Text(elapsedTimeText)
                    .foregroundStyle(urgencyColor)
            }
        }
    }

    // MARK: - History

    private var historySection: some View {
        Section {
            if let viewModel {
                if viewModel.isLoadingLogs {
                    ProgressView("Loading history…")
                } else if viewModel.completionLogs.isEmpty {
                    ContentUnavailableView {
                        Label("No History", systemImage: "clock")
                    } description: {
                        Text("Log your first completion from the home screen.")
                    }
                } else {
                    ForEach(viewModel.completionLogs, id: \.id) { log in
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(Self.dateFormatter.string(from: log.completedAt))
                                    .font(.subheadline)
                                Text(Self.relativeFormatter.localizedString(for: log.completedAt, relativeTo: Date()))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundStyle(.green)
                                .accessibilityHidden(true)
                        }
                        .accessibilityElement(children: .combine)
                        .accessibilityLabel("Completed \(Self.dateFormatter.string(from: log.completedAt)), \(Self.relativeFormatter.localizedString(for: log.completedAt, relativeTo: Date()))")
                    }

                    if !RevenueCatService.shared.isPremium && viewModel.totalLogCount > FreeTierLimits.maxHistoryPerItem {
                        Button {
                            showingPaywall = true
                        } label: {
                            HStack {
                                Image(systemName: "lock.fill")
                                    .foregroundStyle(.accent)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("See full history")
                                        .font(.subheadline)
                                        .fontWeight(.medium)
                                    Text("\(viewModel.totalLogCount - FreeTierLimits.maxHistoryPerItem) more entries \u{2022} Upgrade to Premium")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .accessibilityLabel("See full history, \(viewModel.totalLogCount - FreeTierLimits.maxHistoryPerItem) more entries")
                        .accessibilityHint("Upgrade to Premium to view all completion history")
                    }
                }
            } else {
                ProgressView()
            }
        } header: {
            Text("Completion History")
        }
    }

    // MARK: - Helpers

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
        if ratio > 1.0 { return .red }
        if ratio >= 0.75 { return .yellow }
        return .green
    }

    private func intervalText(days: Int) -> String {
        if days % 365 == 0 {
            let years = days / 365
            return years == 1 ? "Every year" : "Every \(years) years"
        } else if days % 30 == 0 {
            let months = days / 30
            return months == 1 ? "Every month" : "Every \(months) months"
        } else if days % 7 == 0 {
            let weeks = days / 7
            return weeks == 1 ? "Every week" : "Every \(weeks) weeks"
        } else {
            return days == 1 ? "Every day" : "Every \(days) days"
        }
    }
}

// MARK: - Edit Tracker Sheet (wraps AddTrackerView pattern)

private struct EditTrackerSheet: View {
    let item: TrackerItem
    let modelContext: ModelContext
    let onDismiss: () -> Void

    @State private var viewModel: HomeViewModel?

    var body: some View {
        Group {
            if let viewModel {
                AddTrackerView(viewModel: viewModel, editingItem: item)
            } else {
                ProgressView()
            }
        }
        .onAppear {
            if viewModel == nil {
                viewModel = HomeViewModel(modelContext: modelContext)
            }
        }
        .onDisappear {
            onDismiss()
        }
    }
}

#Preview {
    NavigationStack {
        TrackerDetailView(item: TrackerItem(
            name: "Oil Change",
            categoryId: UUID(),
            reminderIntervalDays: 90,
            iconName: "car.fill"
        ))
    }
    .modelContainer(for: [TrackerItem.self, CompletionLog.self, TrackerCategory.self], inMemory: true)
}
