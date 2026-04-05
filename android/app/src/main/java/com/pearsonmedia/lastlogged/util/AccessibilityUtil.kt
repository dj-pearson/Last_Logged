package com.pearsonmedia.lastlogged.util

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem

/**
 * Accessibility utilities for TalkBack and screen reader support.
 * Ensures minimum 48dp touch targets and proper content descriptions.
 */
object AccessibilityUtil {

    fun trackerRowDescription(item: TrackerItem): String {
        val elapsed = TimeFormatUtil.elapsedTimeString(item.lastCompletedAt)
        val urgency = TimeFormatUtil.urgencyLevel(item.lastCompletedAt, item.reminderIntervalDays)
        val urgencyText = when (urgency) {
            TimeFormatUtil.UrgencyLevel.GOOD -> "up to date"
            TimeFormatUtil.UrgencyLevel.DUE_SOON -> "due soon"
            TimeFormatUtil.UrgencyLevel.OVERDUE -> "overdue"
        }
        return "${item.name}, last logged $elapsed, $urgencyText"
    }

    fun logButtonDescription(itemName: String): String {
        return "Log completion for $itemName"
    }

    fun archiveActionDescription(itemName: String): String {
        return "Archive $itemName"
    }

    fun editActionDescription(itemName: String): String {
        return "Edit $itemName"
    }

    /**
     * Modifier for live region announcements (loading states, snackbars).
     */
    fun Modifier.liveRegionPolite(description: String): Modifier = this.semantics {
        contentDescription = description
        liveRegion = LiveRegionMode.Polite
    }

    fun Modifier.liveRegionAssertive(description: String): Modifier = this.semantics {
        contentDescription = description
        liveRegion = LiveRegionMode.Assertive
    }

    /**
     * Returns true when the user has reduced the system animator duration scale to 0,
     * i.e. "Remove animations" accessibility setting is enabled. Premium animations
     * should be gated behind !isReduceMotionEnabled(context).
     */
    fun isReduceMotionEnabled(context: Context): Boolean {
        return try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            isReduceMotionFromScale(scale)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Pure helper for unit-testing the reduced-motion policy independently of
     * Android's Settings.Global. Treats any scale <= 0 as "animations removed".
     */
    fun isReduceMotionFromScale(animatorDurationScale: Float): Boolean =
        animatorDurationScale <= 0f

    @Composable
    fun rememberReduceMotion(): Boolean {
        val context = LocalContext.current
        return remember(context) { isReduceMotionEnabled(context) }
    }
}
