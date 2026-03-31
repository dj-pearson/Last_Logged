import Foundation
import TelemetryDeck

final class AnalyticsService {
    static let shared = AnalyticsService()

    private init() {}

    // MARK: - Configuration

    func configure() {
        let config = TelemetryDeck.Config(appID: "YOUR_TELEMETRYDECK_APP_ID")
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

    func trackTrialStarted() {
        TelemetryDeck.signal("trial_started")
    }

    func trackCategorySelected() {
        TelemetryDeck.signal("category_selected")
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
