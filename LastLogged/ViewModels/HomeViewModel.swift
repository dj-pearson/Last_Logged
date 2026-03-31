import Foundation
import SwiftData

@Observable
final class HomeViewModel {
    private var modelContext: ModelContext

    var trackerItems: [TrackerItem] = []
    var categories: [TrackerCategory] = []
    var lastError: String?

    init(modelContext: ModelContext) {
        self.modelContext = modelContext
        fetchItems()
        fetchCategories()
    }

    // MARK: - Free Tier

    var canCreateTracker: Bool {
        RevenueCatService.shared.isPremium || trackerItems.count < FreeTierLimits.maxTrackers
    }

    // MARK: - Fetch

    func fetchItems() {
        let descriptor = FetchDescriptor<TrackerItem>(
            predicate: #Predicate { !$0.isArchived },
            sortBy: [SortDescriptor(\.sortOrder)]
        )
        do {
            trackerItems = try modelContext.fetch(descriptor)
        } catch {
            trackerItems = []
            AnalyticsService.shared.trackError("fetch_items", error: error)
        }
    }

    func fetchCategories() {
        let descriptor = FetchDescriptor<TrackerCategory>(
            sortBy: [SortDescriptor(\.sortOrder)]
        )
        do {
            categories = try modelContext.fetch(descriptor)
        } catch {
            categories = []
            AnalyticsService.shared.trackError("fetch_categories", error: error)
        }
    }

    // MARK: - Grouped Items

    var itemsByCategory: [(category: TrackerCategory, items: [TrackerItem])] {
        categories.compactMap { category in
            let items = trackerItems.filter { $0.categoryId == category.id }
            guard !items.isEmpty else { return nil }
            return (category: category, items: items)
        }
    }

    // MARK: - Sorted by Most Overdue

    var itemsSortedByOverdue: [TrackerItem] {
        trackerItems.sorted { a, b in
            overdueScore(for: a) > overdueScore(for: b)
        }
    }

    private func overdueScore(for item: TrackerItem) -> Double {
        guard let interval = item.reminderIntervalDays, interval > 0 else {
            // No reminder interval — treat as lowest priority
            return item.lastCompletedAt == nil ? 0.5 : 0.0
        }
        guard let lastCompleted = item.lastCompletedAt else {
            // Never completed with a reminder interval — most overdue
            return Double.greatestFiniteMagnitude
        }
        let elapsed = Date().timeIntervalSince(lastCompleted)
        let intervalSeconds = Double(interval) * 86400
        return elapsed / intervalSeconds
    }

    // MARK: - Create

    func createItem(
        name: String,
        categoryId: UUID,
        reminderIntervalDays: Int?,
        iconName: String
    ) {
        let maxSortOrder = trackerItems
            .filter { $0.categoryId == categoryId }
            .map(\.sortOrder)
            .max() ?? -1

        let item = TrackerItem(
            name: name,
            categoryId: categoryId,
            reminderIntervalDays: reminderIntervalDays,
            sortOrder: maxSortOrder + 1,
            iconName: iconName
        )
        modelContext.insert(item)
        save()
        fetchItems()
        scheduleNotifications()
        triggerSync()
        AnalyticsService.shared.trackItemCreated()
    }

    // MARK: - Archive

    func archiveItem(_ item: TrackerItem) {
        item.isArchived = true
        item.syncStatus = .pending
        save()
        fetchItems()
        scheduleNotifications()
        triggerSync()
    }

    // MARK: - Log Completion

    struct LogUndoInfo {
        let completionLog: CompletionLog
        let previousLastCompletedAt: Date?
        let item: TrackerItem
    }

    func logCompletion(for item: TrackerItem) -> LogUndoInfo {
        let previousDate = item.lastCompletedAt
        let log = CompletionLog(trackerItemId: item.id)
        modelContext.insert(log)
        item.lastCompletedAt = log.completedAt
        item.syncStatus = .pending
        save()
        fetchItems()
        scheduleNotifications()
        triggerSync()
        AnalyticsService.shared.trackItemLogged()
        return LogUndoInfo(completionLog: log, previousLastCompletedAt: previousDate, item: item)
    }

    func undoLog(_ info: LogUndoInfo) {
        modelContext.delete(info.completionLog)
        info.item.lastCompletedAt = info.previousLastCompletedAt
        info.item.syncStatus = .pending
        save()
        fetchItems()
        scheduleNotifications()
        triggerSync()
    }

    // MARK: - Update Item

    func updateItem(
        _ item: TrackerItem,
        name: String,
        categoryId: UUID,
        reminderIntervalDays: Int?,
        iconName: String
    ) {
        item.name = name
        item.categoryId = categoryId
        item.reminderIntervalDays = reminderIntervalDays
        item.iconName = iconName
        item.syncStatus = .pending
        save()
        fetchItems()
        scheduleNotifications()
        triggerSync()
    }

    // MARK: - Update Sort Order

    func updateSortOrder(for item: TrackerItem, newOrder: Int) {
        item.sortOrder = newOrder
        save()
        fetchItems()
    }

    // MARK: - Notifications

    private func scheduleNotifications() {
        let context = modelContext
        Task {
            await NotificationService.shared.rescheduleAllNotifications(modelContext: context)
        }
    }

    // MARK: - Sync

    private func triggerSync() {
        SupabaseService.shared.scheduleSyncAfterWrite(modelContext: modelContext)
    }

    // MARK: - Persistence

    private func save() {
        do {
            try modelContext.save()
            lastError = nil
        } catch {
            lastError = "Failed to save changes. Please try again."
            AnalyticsService.shared.trackError("save_failed", error: error)
        }
    }
}
