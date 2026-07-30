package com.pearsonmedia.lastlogged.util

import android.net.Uri

/**
 * Parses the URIs that can launch the app: the internal `lastlogged://` scheme
 * used by the widget, and the `https://lastlogged.com` web links declared in
 * `.well-known/apple-app-site-association` / `assetlinks.json`.
 *
 * Pure string handling so it is unit-testable without an Android runtime, apart
 * from [parse] which takes an already-built [Uri].
 */
object DeepLinks {

    const val SCHEME = "lastlogged"
    const val WEB_HOST = "lastlogged.com"

    sealed interface Target {
        /** Open a specific tracker's detail screen. */
        data class TrackerDetail(val trackerId: String) : Target

        /** Open the app with no further routing. */
        data object Home : Target
    }

    fun trackerUri(trackerId: String): String = "$SCHEME://tracker/$trackerId"

    /**
     * Returns null when the URI is not ours or is malformed, so callers fall
     * back to normal launch rather than crashing on a hand-typed link.
     */
    fun parse(uri: Uri?): Target? {
        if (uri == null) return null

        val segments = uri.pathSegments.orEmpty()

        return when {
            uri.scheme.equals(SCHEME, ignoreCase = true) -> {
                // lastlogged://tracker/<id> — host carries the first component.
                when (uri.host?.lowercase()) {
                    "tracker" -> segments.firstOrNull()?.let(::trackerTarget)
                    "open" -> Target.Home
                    else -> null
                }
            }

            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals(WEB_HOST, ignoreCase = true) -> {
                // https://lastlogged.com/tracker/<id>
                when (segments.firstOrNull()?.lowercase()) {
                    "tracker" -> segments.getOrNull(1)?.let(::trackerTarget)
                    "open" -> Target.Home
                    "share" -> segments.getOrNull(1)?.let(::trackerTarget)
                    else -> null
                }
            }

            else -> null
        }
    }

    /**
     * Tracker ids are UUIDs written by the app. Validating the shape here keeps
     * a crafted link from being fed straight into a navigation route.
     */
    private fun trackerTarget(rawId: String): Target? {
        val id = rawId.trim()
        if (id.isEmpty() || !UUID_REGEX.matches(id)) return null
        return Target.TrackerDetail(id)
    }

    private val UUID_REGEX =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
}
