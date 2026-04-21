import SwiftUI
import SwiftData
import UIKit

struct QuickLogSheet: View {
    let item: TrackerItem
    var onConfirm: (_ completedAt: Date, _ notes: String?) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var completedAt: Date = Date()
    @State private var notes: String = ""

    private let notesLimit = 500

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    HStack(spacing: 12) {
                        Image(systemName: item.iconName.isEmpty ? "checkmark.seal" : item.iconName)
                            .font(.title2)
                            .foregroundStyle(.tint)
                            .frame(width: 36, height: 36)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.name)
                                .font(.headline)
                            Text("Every \(item.reminderIntervalDays) days")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .accessibilityElement(children: .combine)
                }

                Section("When") {
                    DatePicker(
                        "Completed on",
                        selection: $completedAt,
                        in: ...Date(),
                        displayedComponents: [.date, .hourAndMinute]
                    )
                }

                Section {
                    TextField(
                        "Notes (optional)",
                        text: $notes,
                        axis: .vertical
                    )
                    .lineLimit(3...6)
                    .onChange(of: notes) { _, newValue in
                        if newValue.count > notesLimit {
                            notes = String(newValue.prefix(notesLimit))
                        }
                    }
                } header: {
                    Text("Notes")
                } footer: {
                    if !notes.isEmpty {
                        Text("\(notes.count)/\(notesLimit)")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Log completion")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Log") {
                        UINotificationFeedbackGenerator()
                            .notificationOccurred(.success)
                        let trimmed = notes
                            .trimmingCharacters(in: .whitespacesAndNewlines)
                        onConfirm(completedAt, trimmed.isEmpty ? nil : trimmed)
                        dismiss()
                    }
                    .fontWeight(.semibold)
                }
            }
        }
    }
}

#Preview {
    let container = try! ModelContainer(
        for: TrackerItem.self, CompletionLog.self, TrackerCategory.self,
        configurations: ModelConfiguration(isStoredInMemoryOnly: true)
    )
    let item = TrackerItem(name: "Change HVAC filter", reminderIntervalDays: 90)
    container.mainContext.insert(item)
    return QuickLogSheet(item: item) { _, _ in }
        .modelContainer(container)
}
