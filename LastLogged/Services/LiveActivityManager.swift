import Foundation
#if canImport(ActivityKit)
import ActivityKit

/// Manages Live Activities for "just logged" events. Starts a short-lived
/// activity after a successful completion log so the user gets a premium
/// Dynamic Island + lock-screen acknowledgement. Auto-ends after 5 minutes.
///
/// Gated by iOS 16.1+ availability check and user setting
/// ActivityAuthorizationInfo.areActivitiesEnabled.
@available(iOS 16.1, *)
enum LiveActivityManager {
    /// Starts a new Live Activity for a fresh log. Safe to call even if
    /// Live Activities are disabled — it simply no-ops.
    static func startLogActivity(trackerName: String, iconName: String) {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return }

        let attributes = LastLoggedActivityAttributes(
            trackerName: trackerName,
            iconName: iconName
        )
        let state = LastLoggedActivityAttributes.ContentState(
            loggedAt: Date(),
            relativeLabel: "Just now"
        )

        do {
            let content = ActivityContent(
                state: state,
                staleDate: Date().addingTimeInterval(5 * 60)
            )
            _ = try Activity.request(
                attributes: attributes,
                content: content,
                pushType: nil
            )
        } catch {
            // Silently ignore — Live Activity is a nice-to-have, never
            // block the main log flow if it fails.
        }
    }

    /// Ends all active Last Logged activities. Called on app foreground or
    /// when the user explicitly dismisses.
    static func endAllActivities() {
        Task {
            for activity in Activity<LastLoggedActivityAttributes>.activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
        }
    }
}
#endif
