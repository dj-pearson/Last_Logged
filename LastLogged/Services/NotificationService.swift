import Foundation
import UserNotifications
import SwiftData

final class NotificationService {
    static let shared = NotificationService()

    static let categoryIdentifier = "TRACKER_REMINDER"
    static let logActionIdentifier = "LOG_NOW_ACTION"

    private let center = UNUserNotificationCenter.current()

    private init() {
        registerCategory()
    }

    // MARK: - Category Registration

    private func registerCategory() {
        let logAction = UNNotificationAction(
            identifier: Self.logActionIdentifier,
            title: "Log Now",
            options: [.foreground]
        )
        let category = UNNotificationCategory(
            identifier: Self.categoryIdentifier,
            actions: [logAction],
            intentIdentifiers: [],
            options: []
        )
        center.setNotificationCategories([category])
    }

    // MARK: - Permission

    func requestPermissionIfNeeded() async -> Bool {
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .notDetermined:
            do {
                return try await center.requestAuthorization(options: [.alert, .sound, .badge])
            } catch {
                return false
            }
        case .authorized, .provisional:
            return true
        default:
            return false
        }
    }

    // MARK: - Reschedule All

    @MainActor
    func rescheduleAllNotifications(modelContext: ModelContext) async {
        // Fetch non-archived items with reminder intervals
        let descriptor = FetchDescriptor<TrackerItem>(
            predicate: #Predicate { !$0.isArchived }
        )
        guard let items = try? modelContext.fetch(descriptor) else { return }

        let itemsWithReminders = items.filter { $0.reminderIntervalDays != nil && $0.reminderIntervalDays! > 0 }
        guard !itemsWithReminders.isEmpty else {
            center.removeAllPendingNotificationRequests()
            return
        }

        // Request permission if first time
        let granted = await requestPermissionIfNeeded()
        guard granted else { return }

        // Remove all existing notifications and reschedule
        center.removeAllPendingNotificationRequests()

        // Sort by priority: overdue first (highest overdue ratio), then soonest-due
        let sorted = itemsWithReminders.sorted { a, b in
            duePriority(for: a) > duePriority(for: b)
        }

        // Respect the 64 pending notification limit
        let limit = min(sorted.count, 64)
        for i in 0..<limit {
            let item = sorted[i]
            if let request = makeNotificationRequest(for: item) {
                try? await center.add(request)
            }
        }
    }

    // MARK: - User Reminder Time Preference

    private var preferredHour: Int {
        let val = UserDefaults.standard.integer(forKey: "defaultReminderHour")
        return val == 0 ? 9 : val
    }

    private var preferredMinute: Int {
        UserDefaults.standard.integer(forKey: "defaultReminderMinute")
    }

    // MARK: - Notification Creation

    private func makeNotificationRequest(for item: TrackerItem) -> UNNotificationRequest? {
        guard let intervalDays = item.reminderIntervalDays, intervalDays > 0 else { return nil }

        let dueDate = computeDueDate(for: item)
        let now = Date()
        let hour = preferredHour
        let minute = preferredMinute

        // If due date is in the past, schedule for tomorrow at user's preferred time
        let scheduleDate: Date
        if dueDate <= now {
            let calendar = Calendar.current
            guard let tomorrow = calendar.date(byAdding: .day, value: 1, to: now) else { return nil }
            scheduleDate = calendar.date(bySettingHour: hour, minute: minute, second: 0, of: tomorrow) ?? tomorrow
        } else {
            let calendar = Calendar.current
            scheduleDate = calendar.date(bySettingHour: hour, minute: minute, second: 0, of: dueDate) ?? dueDate
        }

        let elapsedDays = daysSinceLastCompletion(for: item)

        let content = UNMutableNotificationContent()
        content.title = "Last Logged Reminder"
        if let days = elapsedDays {
            content.body = "It's been \(days) day\(days == 1 ? "" : "s") since your last \(item.name). Time to log it!"
        } else {
            content.body = "You haven't logged \(item.name) yet. Time to log it!"
        }
        content.sound = .default
        content.categoryIdentifier = Self.categoryIdentifier
        content.userInfo = ["trackerItemId": item.id.uuidString]

        let calendar = Calendar.current
        let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: scheduleDate)
        let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)

        return UNNotificationRequest(
            identifier: item.id.uuidString,
            content: content,
            trigger: trigger
        )
    }

    // MARK: - Helpers

    private func computeDueDate(for item: TrackerItem) -> Date {
        guard let intervalDays = item.reminderIntervalDays, intervalDays > 0 else {
            return .distantFuture
        }
        let baseDate = item.lastCompletedAt ?? item.createdAt
        return Calendar.current.date(byAdding: .day, value: intervalDays, to: baseDate) ?? .distantFuture
    }

    private func duePriority(for item: TrackerItem) -> Double {
        guard let interval = item.reminderIntervalDays, interval > 0 else { return 0.0 }
        guard let lastCompleted = item.lastCompletedAt else {
            return Double.greatestFiniteMagnitude
        }
        let elapsed = Date().timeIntervalSince(lastCompleted)
        let intervalSeconds = Double(interval) * 86400
        return elapsed / intervalSeconds
    }

    private func daysSinceLastCompletion(for item: TrackerItem) -> Int? {
        guard let lastCompleted = item.lastCompletedAt else { return nil }
        let elapsed = Date().timeIntervalSince(lastCompleted)
        return max(1, Int(elapsed / 86400))
    }
}
