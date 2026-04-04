package com.pearsonmedia.lastlogged.service

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AnalyticsService"
    }

    fun configure() {
        // TODO: Initialize TelemetryDeck or other analytics SDK
        Log.d(TAG, "Analytics configured")
    }

    fun trackEvent(name: String, properties: Map<String, String> = emptyMap()) {
        Log.d(TAG, "Event: $name, properties: $properties")
        // TODO: Send to TelemetryDeck
    }

    fun trackError(name: String, error: String, context: Map<String, String> = emptyMap()) {
        Log.e(TAG, "Error event: $name - $error, context: $context")
        // TODO: Send to TelemetryDeck
    }

    // Convenience methods matching iOS AnalyticsService
    fun trackItemCreated() = trackEvent("item_created")
    fun trackItemLogged() = trackEvent("item_logged")
    fun trackOnboardingCompleted() = trackEvent("onboarding_completed")
    fun trackPaywallPresented() = trackEvent("paywall_presented")
    fun trackPaywallDismissed() = trackEvent("paywall_dismissed")
    fun trackPaywallConverted() = trackEvent("paywall_converted")
    fun trackTrialStarted() = trackEvent("trial_started")
    fun trackAccountDeleted() = trackEvent("account_deleted")
    fun trackSyncConflict() = trackEvent("sync_conflict")
    fun trackWidgetTapped() = trackEvent("widget_tapped")
}
