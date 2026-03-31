import Foundation

// ============================================================
// IMPORTANT: This file contains API keys for third-party services.
//
// For local development:
//   1. Copy AppSecrets.example.swift to AppSecrets.swift
//   2. Replace placeholder values with real keys
//   3. AppSecrets.swift is gitignored — never commit real keys
//
// For CI/CD:
//   Generate this file from environment variables in a build phase.
// ============================================================

enum AppSecrets {
    // Supabase
    static let supabaseURL = "https://YOUR_PROJECT.supabase.co"
    static let supabaseAnonKey = "YOUR_SUPABASE_ANON_KEY"

    // RevenueCat
    static let revenueCatAPIKey = "YOUR_REVENUECAT_API_KEY"

    // TelemetryDeck
    static let telemetryDeckAppID = "YOUR_TELEMETRYDECK_APP_ID"

    /// Call at app launch to crash early if secrets are still placeholders.
    static func validate() {
        let checks: [(key: String, value: String)] = [
            ("SUPABASE_URL", supabaseURL),
            ("SUPABASE_ANON_KEY", supabaseAnonKey),
            ("REVENUECAT_API_KEY", revenueCatAPIKey),
            ("TELEMETRYDECK_APP_ID", telemetryDeckAppID),
        ]

        for check in checks {
            if check.value.hasPrefix("YOUR_") || check.value.contains("your-") {
                #if DEBUG
                // In debug, print a warning but don't crash — allows UI development
                print("⚠️ [AppSecrets] \(check.key) is still a placeholder. Set real credentials in AppSecrets.swift.")
                #else
                fatalError("[AppSecrets] \(check.key) is a placeholder. Configure real credentials before building for release.")
                #endif
            }
        }
    }
}
