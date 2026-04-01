import Foundation
import TelemetryDeck

final class AnalyticsService {
    static let shared = AnalyticsService()

    private init() {}

    // MARK: - Configuration

    func configure() {
        let config = TelemetryDeck.Config(appID: AppSecrets.telemetryDeckAppID)
        TelemetryDeck.initialize(config: config)
    }

    // MARK: - Events

    func trackItemCreated() {
        TelemetryDeck.signal("item_created")
    }

    func trackItemLogged() {
        TelemetryDeck.signal("item_logged")
    }

    func trackWidgetTapped() {
        TelemetryDeck.signal("widget_tapped")
    }

    func trackOnboardingCompleted() {
        TelemetryDeck.signal("onboarding_completed")
    }

    func trackPaywallPresented() {
        TelemetryDeck.signal("paywall_presented")
    }

    func trackPaywallDismissed() {
        TelemetryDeck.signal("paywall_dismissed")
    }

    func trackPaywallConverted() {
        TelemetryDeck.signal("paywall_converted")
    }

    func trackTrialStarted() {
        TelemetryDeck.signal("trial_started")
    }

    func trackCategorySelected() {
        TelemetryDeck.signal("category_selected")
    }

    func trackSyncConflict(itemId: UUID) {
        TelemetryDeck.signal(
            "sync_conflict",
            parameters: ["itemId": itemId.uuidString]
        )
    }

    func trackAccountDeleted() {
        TelemetryDeck.signal("account_deleted")
    }

    func trackEvent(_ name: String) {
        TelemetryDeck.signal(name)
    }

    // MARK: - Error Tracking

    func trackError(_ context: String, error: Error) {
        TelemetryDeck.signal(
            "app_error",
            parameters: [
                "context": context,
                "error": error.localizedDescription,
            ]
        )
    }
}
