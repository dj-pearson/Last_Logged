package com.pearsonmedia.lastlogged

import com.pearsonmedia.lastlogged.util.TimeFormatUtil
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class TimeFormatUtilTest {

    @Test
    fun `elapsedTimeString returns Never for null`() {
        assertEquals("Never", TimeFormatUtil.elapsedTimeString(null))
    }

    @Test
    fun `elapsedTimeString returns Just now for recent time`() {
        val recentTime = System.currentTimeMillis() - 10_000 // 10 seconds ago
        assertEquals("Just now", TimeFormatUtil.elapsedTimeString(recentTime))
    }

    @Test
    fun `elapsedTimeString returns minutes for short durations`() {
        val fiveMinutesAgo = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5)
        assertEquals("5m ago", TimeFormatUtil.elapsedTimeString(fiveMinutesAgo))
    }

    @Test
    fun `elapsedTimeString returns hours for medium durations`() {
        val threeHoursAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(3)
        assertEquals("3h ago", TimeFormatUtil.elapsedTimeString(threeHoursAgo))
    }

    @Test
    fun `elapsedTimeString returns days for longer durations`() {
        val tenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10)
        assertEquals("10d ago", TimeFormatUtil.elapsedTimeString(tenDaysAgo))
    }

    @Test
    fun `urgencyLevel returns OVERDUE for null lastCompletedAt`() {
        assertEquals(
            TimeFormatUtil.UrgencyLevel.OVERDUE,
            TimeFormatUtil.urgencyLevel(null, 30)
        )
    }

    @Test
    fun `urgencyLevel returns GOOD for recently completed`() {
        val yesterday = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        assertEquals(
            TimeFormatUtil.UrgencyLevel.GOOD,
            TimeFormatUtil.urgencyLevel(yesterday, 30)
        )
    }

    @Test
    fun `urgencyLevel returns DUE_SOON at 80 percent of interval`() {
        val twentyFiveDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(25)
        assertEquals(
            TimeFormatUtil.UrgencyLevel.DUE_SOON,
            TimeFormatUtil.urgencyLevel(twentyFiveDaysAgo, 30)
        )
    }

    @Test
    fun `urgencyLevel returns OVERDUE past interval`() {
        val fortyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(40)
        assertEquals(
            TimeFormatUtil.UrgencyLevel.OVERDUE,
            TimeFormatUtil.urgencyLevel(fortyDaysAgo, 30)
        )
    }
}
