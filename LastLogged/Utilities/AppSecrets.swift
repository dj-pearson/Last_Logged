import Foundation

// ============================================================
// IMPORTANT: Secrets are injected via xcconfig, never hardcoded.
//
// Layout:
//   LastLogged/Config/Debug.xcconfig   — wired to Debug builds
//   LastLogged/Config/Release.xcconfig — wired to Release builds
//   LastLogged/Config/Secrets.xcconfig — gitignored; real keys here
//   LastLogged/Config/Secrets.example.xcconfig — committed placeholder
//
// Info.plist declares $(SUPABASE_URL), $(SUPABASE_ANON_KEY),
// $(REVENUECAT_API_KEY), $(TELEMETRYDECK_APP_ID). Those values are
// substituted at build time and read here via Bundle.main.
//
// validate() prints a warning in DEBUG and fatalErrors in RELEASE if
// any value is missing or still a placeholder — a safety net that
// prevents accidentally shipping a placeholder key.
// ============================================================

enum AppSecrets {
    static let supabaseURL: String = infoValue("SUPABASE_URL")
    static let supabaseAnonKey: String = infoValue("SUPABASE_ANON_KEY")
    static let revenueCatAPIKey: String = infoValue("REVENUECAT_API_KEY")
    static let telemetryDeckAppID: String = infoValue("TELEMETRYDECK_APP_ID")

    private static func infoValue(_ key: String) -> String {
        (Bundle.main.object(forInfoDictionaryKey: key) as? String) ?? ""
    }

    static func validate() {
        let checks: [(key: String, value: String)] = [
            ("SUPABASE_URL", supabaseURL),
            ("SUPABASE_ANON_KEY", supabaseAnonKey),
            ("REVENUECAT_API_KEY", revenueCatAPIKey),
            ("TELEMETRYDECK_APP_ID", telemetryDeckAppID),
        ]

        for check in checks {
            let value = check.value
            let isPlaceholder = value.isEmpty
                || value.hasPrefix("YOUR_")
                || value.contains("YOUR_PROJECT")
                || value.contains("your-")
            if isPlaceholder {
                #if DEBUG
                print("⚠️ [AppSecrets] \(check.key) is still a placeholder. Fill in Config/Secrets.xcconfig.")
                #else
                fatalError("[AppSecrets] \(check.key) is a placeholder. Configure real credentials before building for release.")
                #endif
            }
        }
    }
}
