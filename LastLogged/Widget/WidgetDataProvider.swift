import Foundation
import SwiftData

struct OverdueItem {
    let id: UUID
    let name: String
    let iconName: String
    let categoryName: String
    let categoryColorHex: String
    let lastCompletedAt: Date?
    let reminderIntervalDays: Int?
    let overdueScore: Double

    var elapsedTimeDescription: String {
        guard let lastCompletedAt else { return "Never logged" }
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .full
        return formatter.localizedString(for: lastCompletedAt, relativeTo: Date())
    }

    var urgencyLevel: UrgencyLevel {
        guard let reminderIntervalDays, reminderIntervalDays > 0 else { return .neutral }
        guard let lastCompletedAt else { return .overdue }
        let elapsed = Date().timeIntervalSince(lastCompletedAt)
        let interval = Double(reminderIntervalDays) * 86400
        let ratio = elapsed / interval
        if ratio > 1.0 { return .overdue }
        if ratio >= 0.75 { return .approaching }
        return .onSchedule
    }
}

enum UrgencyLevel {
    case onSchedule
    case approaching
    case overdue
    case neutral
}

class WidgetDataProvider {
    static let shared = WidgetDataProvider()

    private init() {}

    func makeSharedModelContainer() -> ModelContainer? {
        let schema = Schema([
            TrackerItem.self,
            CompletionLog.self,
            TrackerCategory.self,
        ])

        guard let appGroupURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: "group.com.pearsonmedia.lastlogged"
        ) else {
            return nil
        }

        let storeURL = appGroupURL.appending(path: "LastLogged.sqlite")
        let configuration = ModelConfiguration(url: storeURL)

        return try? ModelContainer(for: schema, configurations: [configuration])
    }

    func getTopOverdueItems(count: Int) -> [OverdueItem] {
        guard let container = makeSharedModelContainer() else { return [] }
        let context = ModelContext(container)

        let itemDescriptor = FetchDescriptor<TrackerItem>(
            predicate: #Predicate { !$0.isArchived }
        )
        let categoryDescriptor = FetchDescriptor<TrackerCategory>()

        guard let items = try? context.fetch(itemDescriptor),
              let categories = try? context.fetch(categoryDescriptor) else {
            return []
        }

        let categoryMap = Dictionary(uniqueKeysWithValues: categories.map { ($0.id, $0) })

        let overdueItems: [OverdueItem] = items.map { item in
            let category = categoryMap[item.categoryId]
            let score = overdueScore(for: item)

            return OverdueItem(
                id: item.id,
                name: item.name,
                iconName: item.iconName,
                categoryName: category?.name ?? "Uncategorized",
                categoryColorHex: category?.colorHex ?? "#888888",
                lastCompletedAt: item.lastCompletedAt,
                reminderIntervalDays: item.reminderIntervalDays,
                overdueScore: score
            )
        }

        return Array(
            overdueItems
                .sorted { $0.overdueScore > $1.overdueScore }
                .prefix(count)
        )
    }

    private func overdueScore(for item: TrackerItem) -> Double {
        guard let intervalDays = item.reminderIntervalDays, intervalDays > 0 else {
            return -1.0
        }
        guard let lastCompleted = item.lastCompletedAt else {
            return Double.greatestFiniteMagnitude
        }
        let elapsed = Date().timeIntervalSince(lastCompleted)
        let interval = Double(intervalDays) * 86400
        return elapsed / interval
    }
}
