package com.pearsonmedia.lastlogged.util

/**
 * Milestone thresholds that trigger a celebration when crossed on a log.
 * Kept intentionally short and opinionated so celebrations feel special.
 */
object StreakUtil {
    val MILESTONES: List<Int> = listOf(3, 7, 14, 30, 100)

    /**
     * Returns the milestone value if the new log count exactly matches a threshold,
     * otherwise null. Used to fire a one-shot celebration event.
     */
    fun milestoneFor(logCount: Int): Int? =
        if (logCount in MILESTONES) logCount else null

    /**
     * Returns a copy label describing the milestone ("3 logs!", "7-log streak!", etc.).
     */
    fun labelFor(milestone: Int): String = when (milestone) {
        3 -> "3 logs strong!"
        7 -> "7-log streak!"
        14 -> "Two weeks in!"
        30 -> "30 logs — incredible!"
        100 -> "100 logs — legendary!"
        else -> "$milestone logs!"
    }
}
