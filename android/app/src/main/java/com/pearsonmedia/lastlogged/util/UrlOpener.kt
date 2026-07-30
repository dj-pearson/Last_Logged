package com.pearsonmedia.lastlogged.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens external links in a Chrome Custom Tab.
 *
 * Both stores require Terms and Privacy to be reachable from the purchase
 * surface, so these links must never silently do nothing — hence the layered
 * fallback down to a plain `ACTION_VIEW`.
 */
object UrlOpener {

    private const val TAG = "UrlOpener"

    const val TERMS_URL = "https://lastlogged.com/terms"
    const val PRIVACY_URL = "https://lastlogged.com/privacy"

    fun open(context: Context, url: String) {
        val uri = Uri.parse(url)

        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, uri)
            return
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No Custom Tabs provider for $url: ${e.message}")
        }

        // No Custom Tabs provider (some AOSP builds, work profiles) — fall back
        // to whatever the device does have.
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No browser available to open $url: ${e.message}")
        }
    }

    fun openTerms(context: Context) = open(context, TERMS_URL)

    fun openPrivacy(context: Context) = open(context, PRIVACY_URL)
}
