package com.pearsonmedia.lastlogged

import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import com.pearsonmedia.lastlogged.util.AccessibilityUtil
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityUtilTest {

    private fun createItem(
        name: String,
        lastCompletedAt: Long? = null,
        reminderIntervalDays: Int = 30
    ) = TrackerItem(
        id = "test-id",
        name = name,
        lastCompletedAt = lastCompletedAt,
        reminderIntervalDays = reminderIntervalDays
    )

    @Test
    fun `trackerRowDescription includes item name`() {
        val item = createItem("HVAC Filter")
        val desc = AccessibilityUtil.trackerRowDescription(item)
        assertTrue(desc.contains("HVAC Filter"))
    }

    @Test
    fun `trackerRowDescription includes elapsed time`() {
        val item = createItem("Test", lastCompletedAt = null)
        val desc = AccessibilityUtil.trackerRowDescription(item)
        assertTrue(desc.contains("Never"))
    }

    @Test
    fun `trackerRowDescription includes overdue status for null completion`() {
        val item = createItem("Test", lastCompletedAt = null)
        val desc = AccessibilityUtil.trackerRowDescription(item)
        assertTrue(desc.contains("overdue"))
    }

    @Test
    fun `trackerRowDescription includes good status for recent completion`() {
        val recent = System.currentTimeMillis() - 86400000L // 1 day ago
        val item = createItem("Test", lastCompletedAt = recent, reminderIntervalDays = 30)
        val desc = AccessibilityUtil.trackerRowDescription(item)
        assertTrue(desc.contains("up to date"))
    }

    @Test
    fun `logButtonDescription includes item name`() {
        val desc = AccessibilityUtil.logButtonDescription("HVAC Filter")
        assertTrue(desc.contains("HVAC Filter"))
        assertTrue(desc.contains("Log"))
    }

    @Test
    fun `archiveActionDescription includes item name`() {
        val desc = AccessibilityUtil.archiveActionDescription("Oil Change")
        assertTrue(desc.contains("Oil Change"))
        assertTrue(desc.contains("Archive"))
    }

    @Test
    fun `editActionDescription includes item name`() {
        val desc = AccessibilityUtil.editActionDescription("Dental")
        assertTrue(desc.contains("Dental"))
        assertTrue(desc.contains("Edit"))
    }

    @Test
    fun `isReduceMotionFromScale true when scale is zero`() {
        assertTrue(AccessibilityUtil.isReduceMotionFromScale(0f))
    }

    @Test
    fun `isReduceMotionFromScale false when scale is one`() {
        assertTrue(!AccessibilityUtil.isReduceMotionFromScale(1f))
    }

    @Test
    fun `isReduceMotionFromScale false for half scale`() {
        assertTrue(!AccessibilityUtil.isReduceMotionFromScale(0.5f))
    }

    @Test
    fun `isReduceMotionFromScale true for negative scale`() {
        assertTrue(AccessibilityUtil.isReduceMotionFromScale(-1f))
    }
}
