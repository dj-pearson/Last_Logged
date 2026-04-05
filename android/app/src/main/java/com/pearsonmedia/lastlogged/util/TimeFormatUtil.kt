package com.pearsonmedia.lastlogged.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeFormatUtil {

    private val dateFormatter: SimpleDateFormat by lazy {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    }

    fun formatDate(timestampMillis: Long): String =
        dateFormatter.format(Date(timestampMillis))

    fun elapsedTimeString(lastCompletedAt: Long?): String {
        if (lastCompletedAt == null) return "Never"

        val now = System.currentTimeMillis()
        val diff = now - lastCompletedAt

        val days = TimeUnit.MILLISECONDS.toDays(diff)
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)

        return when {
            days > 365 -> "${days / 365}y ${(days % 365) / 30}mo ago"
            days > 30 -> "${days / 30}mo ${days % 30}d ago"
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "Just now"
        }
    }

    enum class UrgencyLevel { GOOD, DUE_SOON, OVERDUE }

    fun urgencyLevel(lastCompletedAt: Long?, reminderIntervalDays: Int): UrgencyLevel {
        if (lastCompletedAt == null) return UrgencyLevel.OVERDUE

        val daysSince = TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis() - lastCompletedAt
        )

        return when {
            daysSince >= reminderIntervalDays -> UrgencyLevel.OVERDUE
            daysSince >= reminderIntervalDays * 0.8 -> UrgencyLevel.DUE_SOON
            else -> UrgencyLevel.GOOD
        }
    }
}
