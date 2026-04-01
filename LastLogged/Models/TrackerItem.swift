import Foundation
import SwiftData

@Model
final class TrackerItem {
    @Attribute(.unique) var id: UUID
    var name: String
    var categoryId: UUID
    var reminderIntervalDays: Int?
    var lastCompletedAt: Date?
    var createdAt: Date
    var sortOrder: Int
    var iconName: String
    var isArchived: Bool
    var syncStatus: SyncStatus
    var updatedAt: Date

    init(
        id: UUID = UUID(),
        name: String,
        categoryId: UUID,
        reminderIntervalDays: Int? = nil,
        lastCompletedAt: Date? = nil,
        createdAt: Date = Date(),
        sortOrder: Int = 0,
        iconName: String = "checkmark.circle",
        isArchived: Bool = false,
        syncStatus: SyncStatus = .pending,
        updatedAt: Date = Date()
    ) {
        self.id = id
        self.name = name
        self.categoryId = categoryId
        self.reminderIntervalDays = reminderIntervalDays
        self.lastCompletedAt = lastCompletedAt
        self.createdAt = createdAt
        self.sortOrder = sortOrder
        self.iconName = iconName
        self.isArchived = isArchived
        self.syncStatus = syncStatus
        self.updatedAt = updatedAt
    }
}
