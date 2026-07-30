package com.pearsonmedia.lastlogged

import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import com.pearsonmedia.lastlogged.util.WidgetItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetItemsTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun item(
        id: String,
        name: String = id,
        daysAgo: Long? = 0,
        intervalDays: Int = 30,
        archived: Boolean = false,
        sortOrder: Int = 0
    ) = TrackerItem(
        id = id,
        name = name,
        reminderIntervalDays = intervalDays,
        lastCompletedAt = daysAgo?.let { now - it * day },
        isArchived = archived,
        sortOrder = sortOrder
    )

    @Test
    fun `score is the fraction of the interval elapsed`() {
        val halfway = item("a", daysAgo = 15, intervalDays = 30)
        assertEquals(0.5, WidgetItems.overdueScore(halfway, now), 0.001)

        val due = item("b", daysAgo = 30, intervalDays = 30)
        assertEquals(1.0, WidgetItems.overdueScore(due, now), 0.001)

        val doubleOverdue = item("c", daysAgo = 60, intervalDays = 30)
        assertEquals(2.0, WidgetItems.overdueScore(doubleOverdue, now), 0.001)
    }

    @Test
    fun `never-logged trackers outrank everything`() {
        val neverLogged = item("never", daysAgo = null)
        val wildlyOverdue = item("late", daysAgo = 3650, intervalDays = 30)

        assertTrue(
            WidgetItems.overdueScore(neverLogged, now) >
                WidgetItems.overdueScore(wildlyOverdue, now)
        )
    }

    @Test
    fun `trackers without a reminder interval sort last`() {
        val noInterval = item("none", daysAgo = 500, intervalDays = 0)
        val onSchedule = item("fresh", daysAgo = 0, intervalDays = 30)

        assertEquals(-1.0, WidgetItems.overdueScore(noInterval, now), 0.001)
        assertTrue(
            WidgetItems.overdueScore(onSchedule, now) >
                WidgetItems.overdueScore(noInterval, now)
        )
    }

    @Test
    fun `relative lateness beats absolute lateness`() {
        // 3 days late on a weekly task is more urgent than 3 days late on an
        // annual one — this is why the score is a ratio, not a day count.
        val weekly = item("weekly", daysAgo = 10, intervalDays = 7)
        val annual = item("annual", daysAgo = 368, intervalDays = 365)

        val top = WidgetItems.topOverdue(listOf(annual, weekly), count = 2, now = now)
        assertEquals(listOf("weekly", "annual"), top.map { it.id })
    }

    @Test
    fun `topOverdue orders most overdue first and respects the count`() {
        val items = listOf(
            item("fresh", daysAgo = 1, intervalDays = 30),
            item("overdue", daysAgo = 90, intervalDays = 30),
            item("due-soon", daysAgo = 27, intervalDays = 30)
        )

        val top = WidgetItems.topOverdue(items, count = 2, now = now)

        assertEquals(2, top.size)
        assertEquals(listOf("overdue", "due-soon"), top.map { it.id })
    }

    @Test
    fun `archived trackers are never shown`() {
        val items = listOf(
            item("archived", daysAgo = 900, intervalDays = 30, archived = true),
            item("active", daysAgo = 40, intervalDays = 30)
        )

        val top = WidgetItems.topOverdue(items, count = 5, now = now)

        assertEquals(listOf("active"), top.map { it.id })
    }

    @Test
    fun `ties break deterministically so the widget does not reshuffle`() {
        val a = item("a", name = "Zebra", daysAgo = 30, intervalDays = 30, sortOrder = 2)
        val b = item("b", name = "Apple", daysAgo = 30, intervalDays = 30, sortOrder = 1)

        val first = WidgetItems.topOverdue(listOf(a, b), count = 2, now = now)
        val second = WidgetItems.topOverdue(listOf(b, a), count = 2, now = now)

        assertEquals(first.map { it.id }, second.map { it.id })
        assertEquals(listOf("b", "a"), first.map { it.id })
    }

    @Test
    fun `empty input and non-positive count are handled`() {
        assertEquals(emptyList<TrackerItem>(), WidgetItems.topOverdue(emptyList(), 5, now))
        assertEquals(
            emptyList<TrackerItem>(),
            WidgetItems.topOverdue(listOf(item("a")), count = 0, now = now)
        )
    }
}
