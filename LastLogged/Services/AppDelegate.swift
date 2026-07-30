import UIKit
import UserNotifications

/// Hosts the UIKit-only callbacks SwiftUI's `App` lifecycle does not expose.
///
/// The reminder digest sent by `edge-functions/src/reminder-digest.ts` is
/// addressed to rows in Supabase's `user_devices` table. Without APNs
/// registration the app never contributes a row, so digests have nowhere to go —
/// this delegate is what puts an iOS token in that table.
final class AppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    // MARK: - Remote Notification Registration

    /// Registers for APNs, but only once the user has actually granted
    /// notification permission — registering earlier would prompt nothing and
    /// produce a token for a user who has opted out.
    ///
    /// Safe to call on every launch: APNs tokens rotate (OS upgrade, restore
    /// from backup, app reinstall), and re-registering is how a stale token in
    /// `user_devices` gets replaced.
    @MainActor
    static func registerForPushIfAuthorized() async {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        guard settings.authorizationStatus == .authorized
                || settings.authorizationStatus == .provisional else {
            return
        }
        UIApplication.shared.registerForRemoteNotifications()
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        let token = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()

        Task {
            await SupabaseService.shared.registerDeviceToken(token)
        }
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Expected on the Simulator and when the device is offline. Never fatal:
        // local notifications keep working, the user just misses server digests.
        AnalyticsService.shared.trackError("apns_registration_failed", error: error)
    }
}

// MARK: - UNUserNotificationCenterDelegate

extension AppDelegate: UNUserNotificationCenterDelegate {

    /// Without this, a reminder that fires while the app is open is swallowed
    /// silently — the user sees nothing and assumes reminders are broken.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }
}
