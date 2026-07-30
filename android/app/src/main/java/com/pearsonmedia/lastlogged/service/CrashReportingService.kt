package com.pearsonmedia.lastlogged.service

import android.content.Context
import android.util.Log
import com.pearsonmedia.lastlogged.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Crash and unhandled-error reporting.
 *
 * Gated on `SENTRY_DSN` being set, so debug and CI builds send nothing and need
 * no Sentry project.
 *
 * Privacy: tracker names, notes, and emails are user content and must never
 * reach a third party. PII collection is off and the event/breadcrumb hooks
 * strip anything that could carry it — the Play Data Safety answers depend on
 * that staying true.
 */
@Singleton
class CrashReportingService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CrashReporting"
    }

    private val dsn: String = BuildConfig.SENTRY_DSN

    private val isConfigured: Boolean
        get() = dsn.isNotBlank() && !dsn.startsWith("your-")

    fun configure() {
        if (!isConfigured) {
            Log.d(TAG, "No SENTRY_DSN configured — crash reporting disabled")
            return
        }

        SentryAndroid.init(context) { options ->
            options.dsn = dsn
            options.environment = if (BuildConfig.DEBUG) "debug" else "production"
            options.release = "${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"

            // Never attach IP addresses or usernames.
            options.isSendDefaultPii = false

            // A screenshot or view hierarchy would capture the user's entire
            // tracker list — exactly the content we promise not to collect.
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false

            options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.2

            options.beforeSend = io.sentry.SentryOptions.BeforeSendCallback { event, _ ->
                event.user = null
                event.serverName = null
                event.setExtras(emptyMap())
                event
            }

            options.beforeBreadcrumb = io.sentry.SentryOptions.BeforeBreadcrumbCallback { crumb, _ ->
                // Breadcrumb messages can include screen titles built from
                // tracker names; keep the category, drop the content.
                crumb.message = null
                crumb.data.clear()
                crumb
            }
        }
    }

    /**
     * Reports a handled error with a stable, developer-authored context label.
     * Mirrors [AnalyticsService.trackError] so call sites can report to both.
     */
    fun capture(throwable: Throwable, context: String) {
        Log.e(TAG, "Captured error in $context: ${throwable.message}")
        if (!isConfigured) return

        Sentry.withScope { scope ->
            scope.level = SentryLevel.ERROR
            scope.setTag("context", context)
            Sentry.captureException(throwable)
        }
    }
}
