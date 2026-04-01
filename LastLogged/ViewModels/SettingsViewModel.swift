import Foundation
import SwiftData

@Observable
final class SettingsViewModel {
    private let modelContext: ModelContext

    // MARK: - Account State (reads from singletons)

    var isSignedIn: Bool {
        SupabaseService.shared.isSignedIn
    }

    var userEmail: String? {
        SupabaseService.shared.currentUserEmail
    }

    var subscriptionTierLabel: String {
        switch RevenueCatService.shared.subscriptionTier {
        case .free: return "Free"
        case .premium: return "Premium"
        case .lifetime: return "Lifetime"
        }
    }

    var isPremium: Bool {
        RevenueCatService.shared.isPremium
    }

    // MARK: - Notifications

    @ObservationIgnored
    var remindersEnabled: Bool {
        get { UserDefaults.standard.bool(forKey: "remindersEnabled") }
        set { UserDefaults.standard.set(newValue, forKey: "remindersEnabled") }
    }

    @ObservationIgnored
    var defaultReminderHour: Int {
        get {
            let val = UserDefaults.standard.integer(forKey: "defaultReminderHour")
            return val == 0 ? 9 : val // default to 9 AM
        }
        set { UserDefaults.standard.set(newValue, forKey: "defaultReminderHour") }
    }

    @ObservationIgnored
    var defaultReminderMinute: Int {
        get { UserDefaults.standard.integer(forKey: "defaultReminderMinute") }
        set { UserDefaults.standard.set(newValue, forKey: "defaultReminderMinute") }
    }

    var defaultReminderTime: Date {
        get {
            var components = DateComponents()
            components.hour = defaultReminderHour
            components.minute = defaultReminderMinute
            return Calendar.current.date(from: components) ?? Date()
        }
        set {
            let components = Calendar.current.dateComponents([.hour, .minute], from: newValue)
            defaultReminderHour = components.hour ?? 9
            defaultReminderMinute = components.minute ?? 0
        }
    }

    // MARK: - Data

    var showClearDataConfirmation = false
    var isExporting = false
    var isClearingData = false
    var isSigningOut = false
    var lastError: String?

    // MARK: - Navigation

    var showingPaywall = false
    var showingAuth = false
    var showingCancellation = false

    // MARK: - App Info

    var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
    }

    var buildNumber: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
    }

    // MARK: - Init

    init(modelContext: ModelContext) {
        self.modelContext = modelContext
        // Default reminders to enabled if not yet set
        if !UserDefaults.standard.bool(forKey: "remindersEnabledInitialized") {
            UserDefaults.standard.set(true, forKey: "remindersEnabled")
            UserDefaults.standard.set(true, forKey: "remindersEnabledInitialized")
        }
    }

    // MARK: - Actions

    func signOut() {
        isSigningOut = true
        Task { @MainActor in
            do {
                try await SupabaseService.shared.signOut()
            } catch {
                lastError = "Failed to sign out. Please try again."
                AnalyticsService.shared.trackError("sign_out_failed", error: error)
            }
            isSigningOut = false
        }
    }

    func clearAllData() {
        isClearingData = true
        do {
            try modelContext.delete(model: CompletionLog.self)
            try modelContext.delete(model: TrackerItem.self)
            try modelContext.delete(model: TrackerCategory.self)
            try modelContext.save()
            lastError = nil
        } catch {
            lastError = "Failed to clear data. Please try again."
            AnalyticsService.shared.trackError("clear_data_failed", error: error)
        }
        isClearingData = false
    }

    func exportDataLocally() -> String {
        var result: [String: Any] = [:]

        let categoryDescriptor = FetchDescriptor<TrackerCategory>()
        let itemDescriptor = FetchDescriptor<TrackerItem>()
        let logDescriptor = FetchDescriptor<CompletionLog>()

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]

        if let categories = try? modelContext.fetch(categoryDescriptor) {
            result["categories"] = categories.map { cat in
                [
                    "id": cat.id.uuidString,
                    "name": cat.name,
                    "iconName": cat.iconName,
                    "colorHex": cat.colorHex,
                    "sortOrder": cat.sortOrder,
                    "isDefault": cat.isDefault,
                ] as [String: Any]
            }
        }

        if let items = try? modelContext.fetch(itemDescriptor) {
            result["trackerItems"] = items.map { item in
                var dict: [String: Any] = [
                    "id": item.id.uuidString,
                    "name": item.name,
                    "categoryId": item.categoryId.uuidString,
                    "iconName": item.iconName,
                    "sortOrder": item.sortOrder,
                    "isArchived": item.isArchived,
                    "createdAt": formatter.string(from: item.createdAt),
                ]
                if let interval = item.reminderIntervalDays {
                    dict["reminderIntervalDays"] = interval
                }
                if let last = item.lastCompletedAt {
                    dict["lastCompletedAt"] = formatter.string(from: last)
                }
                return dict
            }
        }

        if let logs = try? modelContext.fetch(logDescriptor) {
            result["completionLogs"] = logs.map { log in
                var dict: [String: Any] = [
                    "id": log.id.uuidString,
                    "trackerItemId": log.trackerItemId.uuidString,
                    "completedAt": formatter.string(from: log.completedAt),
                ]
                if let notes = log.notes {
                    dict["notes"] = notes
                }
                return dict
            }
        }

        if let data = try? JSONSerialization.data(withJSONObject: result, options: .prettyPrinted),
           let jsonString = String(data: data, encoding: .utf8) {
            return jsonString
        }
        return "{}"
    }
}
