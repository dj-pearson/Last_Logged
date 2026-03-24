import Foundation
import SwiftData

@Observable
final class TrackerDetailViewModel {
    private var modelContext: ModelContext
    var item: TrackerItem

    init(modelContext: ModelContext, item: TrackerItem) {
        self.modelContext = modelContext
        self.item = item
    }

    func archiveItem() {
        item.isArchived = true
        save()
    }

    func updateItem(
        name: String,
        categoryId: UUID,
        reminderIntervalDays: Int?,
        iconName: String
    ) {
        item.name = name
        item.categoryId = categoryId
        item.reminderIntervalDays = reminderIntervalDays
        item.iconName = iconName
        save()
    }

    private func save() {
        do {
            try modelContext.save()
        } catch {
            // Save failed silently
        }
    }
}
