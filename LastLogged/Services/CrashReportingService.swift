import Foundation
import Sentry

/// Crash and unhandled-error reporting.
///
/// Before this existed, a crash in production was invisible until a user left a
/// review. Everything here is gated on a DSN being configured, so local and CI
/// builds send nothing and need no Sentry project.
///
/// Privacy: tracker names, notes, emails, and auth tokens are user content and
/// must never reach a third party. `beforeSend` scrubs the payload and
/// `sendDefaultPii` is off — the store-listing privacy answers depend on that
/// staying true.
enum CrashReportingService {

    static func configure() {
        guard AppSecrets.isCrashReportingConfigured else {
            #if DEBUG
            print("ℹ️ [CrashReporting] No SENTRY_DSN configured — crash reporting disabled.")
            #endif
            return
        }

        SentrySDK.start { options in
            options.dsn = AppSecrets.sentryDSN

            #if DEBUG
            options.environment = "debug"
            // Full sampling in debug; production is sampled to keep the quota
            // meaningful on a consumer app.
            options.tracesSampleRate = 1.0
            #else
            options.environment = "production"
            options.tracesSampleRate = 0.2
            #endif

            // Never attach IP addresses, usernames, or emails.
            options.sendDefaultPii = false

            // Screenshots and view hierarchies would capture a user's entire
            // tracker list — exactly the content we promise not to collect.
            options.attachScreenshot = false
            options.attachViewHierarchy = false

            options.beforeSend = { event in
                scrub(event)
            }

            options.beforeBreadcrumb = { crumb in
                // Breadcrumb messages can contain view titles built from tracker
                // names, so drop the message and keep only the category.
                crumb.message = nil
                crumb.data = nil
                return crumb
            }
        }
    }

    /// Removes user content from an event before it leaves the device.
    private static func scrub(_ event: Event) -> Event? {
        event.user = nil
        event.serverName = nil

        // `extra` is where ad-hoc context accumulates; the safest policy for a
        // personal-data app is to drop it wholesale.
        event.extra = nil

        return event
    }

    /// Reports a handled error with a stable context label. Mirrors
    /// `AnalyticsService.trackError` so call sites can report to both.
    static func capture(_ error: Error, context: String) {
        guard AppSecrets.isCrashReportingConfigured else { return }

        SentrySDK.capture(error: error) { scope in
            // A short, developer-authored label — never user content.
            scope.setTag(value: context, key: "context")
        }
    }

    /// Sets the release identifier so crashes can be attributed to a build.
    static func setRelease(version: String, build: String) {
        guard AppSecrets.isCrashReportingConfigured else { return }
        SentrySDK.configureScope { scope in
            scope.setTag(value: "\(version) (\(build))", key: "app_version")
        }
    }
}
