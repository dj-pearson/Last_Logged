import Foundation

// ============================================================
// AppSecrets Template
//
// Copy this file to AppSecrets.swift and fill in your real keys.
// AppSecrets.swift is gitignored — never commit real credentials.
// ============================================================

enum AppSecrets {
    // Supabase — from Project Settings > API in your Supabase dashboard
    static let supabaseURL = "https://YOUR_PROJECT.supabase.co"
    static let supabaseAnonKey = "YOUR_SUPABASE_ANON_KEY"

    // RevenueCat — from Project Settings in your RevenueCat dashboard
    static let revenueCatAPIKey = "YOUR_REVENUECAT_API_KEY"

    // TelemetryDeck — from your TelemetryDeck app dashboard
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
                print("⚠️ [AppSecrets] \(check.key) is still a placeholder. Set real credentials in AppSecrets.swift.")
                #else
                fatalError("[AppSecrets] \(check.key) is a placeholder. Configure real credentials before building for release.")
                #endif
            }
        }
    }
}
