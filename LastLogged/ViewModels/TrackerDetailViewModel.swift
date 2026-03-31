import Foundation
import SwiftData

@Observable
final class TrackerDetailViewModel {
    private var modelContext: ModelContext
    var item: TrackerItem
    var completionLogs: [CompletionLog] = []
    var totalLogCount: Int = 0
    var lastError: String?

    init(modelContext: ModelContext, item: TrackerItem) {
        self.modelContext = modelContext
        self.item = item
        fetchCompletionLogs()
    }

    func fetchCompletionLogs() {
        let itemId = item.id
        let descriptor = FetchDescriptor<CompletionLog>(
            predicate: #Predicate { $0.trackerItemId == itemId },
            sortBy: [SortDescriptor(\.completedAt, order: .reverse)]
        )
        do {
            let allLogs = try modelContext.fetch(descriptor)
            totalLogCount = allLogs.count
            let isPremium = RevenueCatService.shared.isPremium
            if isPremium {
                completionLogs = allLogs
            } else {
                completionLogs = Array(allLogs.prefix(FreeTierLimits.maxHistoryPerItem))
            }
        } catch {
            completionLogs = []
            totalLogCount = 0
            AnalyticsService.shared.trackError("fetch_completion_logs", error: error)
        }
    }

    var category: TrackerCategory? {
        let categoryId = item.categoryId
        let descriptor = FetchDescriptor<TrackerCategory>(
            predicate: #Predicate { $0.id == categoryId }
        )
        return try? modelContext.fetch(descriptor).first
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
            lastError = nil
        } catch {
            lastError = "Failed to save changes. Please try again."
            AnalyticsService.shared.trackError("detail_save_failed", error: error)
        }
    }
}
