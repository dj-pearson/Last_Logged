import SwiftUI
import SwiftData

struct HomeView: View {
    @Environment(\.modelContext) private var modelContext
    @State private var viewModel: HomeViewModel?
    @State private var showingAddTracker = false

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
                    Button {
                        showingAddTracker = true
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
        }
    }

    // MARK: - Tracker List

    private func trackerListView(viewModel: HomeViewModel) -> some View {
        List {
            ForEach(viewModel.itemsByCategory, id: \.category.id) { group in
                Section {
                    ForEach(group.items, id: \.id) { item in
                        TrackerRowView(item: item)
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
}

// MARK: - Tracker Row

struct TrackerRowView: View {
    let item: TrackerItem

    var body: some View {
        HStack {
            Image(systemName: item.iconName)
                .foregroundStyle(.accent)
                .frame(width: 28)

            Text(item.name)

            Spacer()

            Text("—")
                .foregroundStyle(.secondary)
                .font(.subheadline)
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
