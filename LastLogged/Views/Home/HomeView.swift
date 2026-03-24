import SwiftUI
import SwiftData
import UIKit

struct HomeView: View {
    @Environment(\.modelContext) private var modelContext
    @State private var viewModel: HomeViewModel?
    @State private var showingAddTracker = false
    @State private var showingTemplatePicker = false
    @State private var editingItem: TrackerItem?
    @State private var itemToArchive: TrackerItem?
    @State private var showArchiveConfirmation = false
    @State private var toastInfo: HomeViewModel.LogUndoInfo?
    @State private var toastDismissTask: Task<Void, Never>?

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
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        Button {
                            showingAddTracker = true
                        } label: {
                            Label("New Tracker", systemImage: "plus")
                        }
                        Button {
                            showingTemplatePicker = true
                        } label: {
                            Label("Add from Templates", systemImage: "list.bullet.rectangle")
                        }
                    } label: {
                        Image(systemName: "plus")
                    }
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
        .confirmationDialog("Archive Tracker?", isPresented: $showArchiveConfirmation, presenting: itemToArchive) { item in
            Button("Archive", role: .destructive) {
                viewModel?.archiveItem(item)
            }
        } message: { item in
            Text("'\(item.name)' will be hidden from your home list.")
        }
        .overlay(alignment: .bottom) {
            if toastInfo != nil {
                toastView
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.3), value: toastInfo != nil)
    }

    // MARK: - Empty State

    private var emptyStateView: some View {
        ContentUnavailableView {
            Label("No Trackers Yet", systemImage: "checkmark.circle.fill")
        } description: {
            Text("Start tracking your recurring life events by adding your first tracker.")
        } actions: {
            Button {
                showingAddTracker = true
            } label: {
                Text("Add Your First Tracker")
            }
            .buttonStyle(.borderedProminent)

            Button {
                showingTemplatePicker = true
            } label: {
                Text("Browse Templates")
            }
            .buttonStyle(.bordered)
        }
    }

    // MARK: - Tracker List

    private func trackerListView(viewModel: HomeViewModel) -> some View {
        List {
            ForEach(viewModel.itemsByCategory, id: \.category.id) { group in
                Section {
                    ForEach(group.items, id: \.id) { item in
                        TrackerRowView(item: item) {
                            logItem(item)
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

    // MARK: - Toast View

    private var toastView: some View {
        HStack(spacing: 12) {
            Text("Logged!")
                .fontWeight(.medium)
            Button("Undo") {
                if let toastInfo {
                    viewModel?.undoLog(toastInfo)
                }
                dismissToast()
            }
            .fontWeight(.semibold)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
        .shadow(radius: 4, y: 2)
        .padding(.bottom, 16)
    }
}

// MARK: - Tracker Row

struct TrackerRowView: View {
    let item: TrackerItem
    let onLog: () -> Void

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

            Text(item.name)

            Spacer()

            Text(elapsedTimeText)
                .foregroundStyle(urgencyColor)
                .font(.subheadline)

            Button {
                onLog()
            } label: {
                Image(systemName: "checkmark.circle.fill")
                    .font(.title2)
                    .foregroundStyle(.green)
            }
            .buttonStyle(.plain)
        }
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
