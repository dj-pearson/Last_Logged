import SwiftUI
import SwiftData

struct CancellationView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext

    var onConfirmCancel: () -> Void

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Spacer()

                Image(systemName: "chart.line.uptrend.xyaxis")
                    .font(.system(size: 56))
                    .foregroundStyle(.accent)

                Text("Are you sure?")
                    .font(.title2)
                    .fontWeight(.bold)

                statsCard

                Text("Canceling will remove access to unlimited trackers, full history, and smart reminders.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)

                Spacer()

                Button {
                    dismiss()
                } label: {
                    Text("Keep My Subscription")
                        .fontWeight(.semibold)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.borderedProminent)
                .accessibilityHint("Stay subscribed and keep all features")

                Button(role: .destructive) {
                    onConfirmCancel()
                    dismiss()
                } label: {
                    Text("Cancel Anyway")
                        .font(.subheadline)
                }
                .frame(minHeight: 44)
                .accessibilityHint("Proceed with cancellation")

                Spacer().frame(height: 8)
            }
            .padding()
            .navigationTitle("Before You Go")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
    }

    // MARK: - Stats Card

    private var statsCard: some View {
        let stats = computeStats()
        return VStack(spacing: 16) {
            HStack(spacing: 24) {
                statItem(value: "\(stats.totalEvents)", label: "Events Logged")
                statItem(value: "\(stats.totalTrackers)", label: "Active Trackers")
            }
            HStack(spacing: 24) {
                statItem(value: stats.trackingSince, label: "Tracking Since")
                statItem(value: "\(stats.monthsActive) mo", label: "Active")
            }
        }
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private func statItem(value: String, label: String) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(.title3)
                .fontWeight(.bold)
                .foregroundStyle(.accent)
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label): \(value)")
    }

    // MARK: - Compute Stats

    private struct UserStats {
        let totalEvents: Int
        let totalTrackers: Int
        let trackingSince: String
        let monthsActive: Int
    }

    private func computeStats() -> UserStats {
        let logDescriptor = FetchDescriptor<CompletionLog>()
        let itemDescriptor = FetchDescriptor<TrackerItem>(
            predicate: #Predicate { !$0.isArchived }
        )

        let logs = (try? modelContext.fetch(logDescriptor)) ?? []
        let items = (try? modelContext.fetch(itemDescriptor)) ?? []

        let earliest = logs.map(\.completedAt).min()
            ?? items.map(\.createdAt).min()
            ?? Date()

        let monthsActive = max(1, Calendar.current.dateComponents([.month], from: earliest, to: Date()).month ?? 1)

        let formatter = DateFormatter()
        formatter.dateFormat = "MMM yyyy"

        return UserStats(
            totalEvents: logs.count,
            totalTrackers: items.count,
            trackingSince: formatter.string(from: earliest),
            monthsActive: monthsActive
        )
    }
}

#Preview {
    CancellationView(onConfirmCancel: {})
        .modelContainer(for: [TrackerItem.self, CompletionLog.self, TrackerCategory.self], inMemory: true)
}
