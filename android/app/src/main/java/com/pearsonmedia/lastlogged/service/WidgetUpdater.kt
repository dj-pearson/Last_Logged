package com.pearsonmedia.lastlogged.service

import android.content.Context
import android.util.Log
import com.pearsonmedia.lastlogged.ui.widget.LastLoggedWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes a refresh to every placed home-screen widget.
 *
 * Glance widgets do not observe Room, so without an explicit nudge the widget
 * keeps showing whatever it rendered last — a tracker logged in the app would
 * stay "overdue" on the home screen until the next system refresh.
 *
 * Injected into `TrackerRepository` so every mutation path (log, create,
 * update, archive, undo, clear) triggers a refresh in one place.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WidgetUpdater"
    }

    suspend fun refresh() {
        try {
            LastLoggedWidget().updateAll(context)
        } catch (e: Exception) {
            // A widget refresh must never take down the write that triggered it.
            Log.w(TAG, "Widget refresh failed: ${e.message}")
        }
    }
}
