package com.pearsonmedia.lastlogged.service

import android.content.Context
import android.util.Log
import com.pearsonmedia.lastlogged.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts product analytics signals to TelemetryDeck.
 *
 * Event names are byte-identical to iOS `AnalyticsService` so both platforms
 * aggregate into the same funnels. TelemetryDeck ships no first-party Android
 * SDK, so this posts to the ingest endpoint directly.
 *
 * Every send is fire-and-forget: analytics must never block, slow, or crash a
 * user action, and it is a complete no-op when `TELEMETRYDECK_APP_ID` is blank.
 */
@Singleton
class AnalyticsService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorageService: SecureStorageService
) {
    companion object {
        private const val TAG = "AnalyticsService"
        private const val INGEST_URL = "https://nom.telemetrydeck.com/v2/"
        private const val TIMEOUT_MS = 10_000

        /**
         * Locally generated fallback identifier used before sign-in. Random per
         * install — never derived from a hardware or advertising id.
         */
        private const val KEY_ANONYMOUS_ID = "analytics_anonymous_id"
    }

    private val appId: String = BuildConfig.TELEMETRYDECK_APP_ID

    private val isEnabled: Boolean get() = appId.isNotBlank()

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Log.w(TAG, "Analytics send failed: ${throwable.message}")
        }
    )

    /** SHA-256 of the Supabase user id; set on sign-in, cleared on sign-out. */
    @Volatile
    private var hashedUserId: String? = null

    fun configure() {
        if (!isEnabled) {
            Log.d(TAG, "TELEMETRYDECK_APP_ID is blank — analytics disabled")
            return
        }
        Log.d(TAG, "Analytics configured")
    }

    /**
     * Attributes subsequent signals to a user without the raw id ever leaving
     * the device. Pass null on sign-out to fall back to the anonymous id.
     */
    fun identify(userId: String?) {
        hashedUserId = userId?.takeIf { it.isNotBlank() }?.let(::sha256)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * Stable per-install id so anonymous sessions do not each look like a new
     * user. Stored in EncryptedSharedPreferences alongside other local state.
     */
    private fun anonymousId(): String {
        secureStorageService.getString(KEY_ANONYMOUS_ID)?.let { return it }
        val generated = UUID.randomUUID().toString()
        secureStorageService.setString(KEY_ANONYMOUS_ID, generated)
        return generated
    }

    fun trackEvent(name: String, properties: Map<String, String> = emptyMap()) {
        if (!isEnabled) return
        scope.launch { send(name, properties) }
    }

    fun trackError(name: String, error: String, context: Map<String, String> = emptyMap()) {
        Log.e(TAG, "Error event: $name - $error")
        if (!isEnabled) return
        // Mirrors the iOS "app_error" signal shape.
        scope.launch {
            send("app_error", context + mapOf("context" to name, "error" to error))
        }
    }

    /** Visible for testing: the exact JSON body posted for a signal. */
    internal fun buildPayload(
        name: String,
        properties: Map<String, String>,
        userIdentifier: String
    ): String = JSONArray().put(
        JSONObject().apply {
            put("appID", appId)
            put("clientUser", userIdentifier)
            put("type", name)
            put("payload", JSONObject(properties.toMap()))
        }
    ).toString()

    private fun send(name: String, properties: Map<String, String>) {
        val body = buildPayload(name, properties, hashedUserId ?: anonymousId())

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(INGEST_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
            }

            OutputStreamWriter(connection.outputStream).use { it.write(body) }

            val code = connection.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "Analytics rejected event '$name' with HTTP $code")
            }
        } catch (e: Exception) {
            // Offline, DNS failure, ingest outage — none of these concern the user.
            Log.w(TAG, "Failed to send '$name': ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }

    // Convenience methods matching iOS AnalyticsService — names must stay identical.
    fun trackItemCreated() = trackEvent("item_created")
    fun trackItemLogged() = trackEvent("item_logged")
    fun trackOnboardingCompleted() = trackEvent("onboarding_completed")
    fun trackPaywallPresented() = trackEvent("paywall_presented")
    fun trackPaywallDismissed() = trackEvent("paywall_dismissed")
    fun trackPaywallConverted() = trackEvent("paywall_converted")
    fun trackTrialStarted() = trackEvent("trial_started")
    fun trackCategorySelected() = trackEvent("category_selected")
    fun trackAccountDeleted() = trackEvent("account_deleted")
    fun trackWidgetTapped() = trackEvent("widget_tapped")
    fun trackSyncConflict(itemId: String? = null) =
        trackEvent("sync_conflict", itemId?.let { mapOf("itemId" to it) } ?: emptyMap())
}
