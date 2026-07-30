package com.pearsonmedia.lastlogged.util

import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem

/**
 * Ordering for the home-screen widget.
 *
 * Kept free of Android dependencies so it is unit-testable, and deliberately
 * mirrors `WidgetDataProvider.overdueScore(for:)` on iOS so both widgets pick
 * the same trackers for the same data.
 */
object WidgetItems {

    private const val MILLIS_PER_DAY = 86_400_000.0

    /**
     * Fraction of the reminder interval that has elapsed. `1.0` means due right
     * now, above `1.0` is overdue, so a plain descending sort puts the most
     * overdue tracker first regardless of how long its interval is — a 7-day
     * task 3 days late outranks a 365-day task 3 days late.
     */
    fun overdueScore(item: TrackerItem, now: Long): Double {
        // No interval means the tracker opted out of reminders; sort it last.
        if (item.reminderIntervalDays <= 0) return -1.0

        // Never logged: nothing is more overdue than something never done.
        val lastCompletedAt = item.lastCompletedAt ?: return Double.MAX_VALUE

        val elapsed = (now - lastCompletedAt).toDouble()
        return elapsed / (item.reminderIntervalDays * MILLIS_PER_DAY)
    }

    /**
     * The [count] most overdue active trackers, most overdue first. Archived
     * items are excluded defensively — the widget must never resurrect
     * something the user archived, even if the caller passes an unfiltered list.
     */
    fun topOverdue(
        items: List<TrackerItem>,
        count: Int,
        now: Long = System.currentTimeMillis()
    ): List<TrackerItem> {
        if (count <= 0) return emptyList()

        return items
            .asSequence()
            .filter { !it.isArchived }
            .sortedWith(
                compareByDescending<TrackerItem> { overdueScore(it, now) }
                    // Stable tiebreak so the widget does not reshuffle between
                    // refreshes when several trackers share a score.
                    .thenBy { it.sortOrder }
                    .thenBy { it.name }
            )
            .take(count)
            .toList()
    }
}
