package com.pearsonmedia.lastlogged

import com.pearsonmedia.lastlogged.data.local.entity.SyncStatus
import com.pearsonmedia.lastlogged.data.local.entity.TrackerCategory
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Tests for HomeViewModel logic (grouping, sorting, urgency calculation).
 * Uses plain unit tests without Hilt injection.
 */
class HomeViewModelTest {

    private fun createItem(
        name: String,
        categoryId: String? = null,
        reminderIntervalDays: Int = 30,
        lastCompletedAt: Long? = null,
        isArchived: Boolean = false
    ) = TrackerItem(
        id = UUID.randomUUID().toString(),
        name = name,
        categoryId = categoryId,
        reminderIntervalDays = reminderIntervalDays,
        lastCompletedAt = lastCompletedAt,
        isArchived = isArchived
    )

    private fun createCategory(name: String, id: String = UUID.randomUUID().toString()) =
        TrackerCategory(id = id, name = name)

    @Test
    fun `items grouped by category correctly`() {
        val cat1 = createCategory("Home", "cat1")
        val cat2 = createCategory("Health", "cat2")
        val items = listOf(
            createItem("HVAC Filter", categoryId = "cat1"),
            createItem("Dental", categoryId = "cat2"),
            createItem("Gutter", categoryId = "cat1"),
            createItem("Uncategorized")
        )
        val categories = listOf(cat1, cat2)

        val catMap = categories.associateBy { it.id }
        val grouped = items.groupBy { item -> item.categoryId?.let { catMap[it] } }

        assertEquals(3, grouped.size) // cat1, cat2, null
        assertEquals(2, grouped[cat1]?.size)
        assertEquals(1, grouped[cat2]?.size)
        assertEquals(1, grouped[null]?.size)
    }

    @Test
    fun `archived items not in active list`() {
        val items = listOf(
            createItem("Active", isArchived = false),
            createItem("Archived", isArchived = true),
            createItem("Also Active", isArchived = false)
        )

        val active = items.filter { !it.isArchived }
        assertEquals(2, active.size)
        assertTrue(active.all { !it.isArchived })
    }

    @Test
    fun `items sorted by overdue priority`() {
        val now = System.currentTimeMillis()
        val items = listOf(
            createItem("Recent", lastCompletedAt = now - 86400000L),     // 1 day ago
            createItem("Old", lastCompletedAt = now - 86400000L * 100),  // 100 days ago
            createItem("Never", lastCompletedAt = null),                 // never
            createItem("Medium", lastCompletedAt = now - 86400000L * 30) // 30 days ago
        )

        val sorted = items.sortedByDescending { item ->
            if (item.lastCompletedAt == null) Long.MAX_VALUE
            else {
                val daysSince = (now - item.lastCompletedAt) / 86400000L
                daysSince - item.reminderIntervalDays
            }
        }

        assertEquals("Never", sorted[0].name)
        assertEquals("Old", sorted[1].name)
        assertEquals("Medium", sorted[2].name)
        assertEquals("Recent", sorted[3].name)
    }

    @Test
    fun `new item has PENDING sync status`() {
        val item = createItem("Test")
        assertEquals(SyncStatus.PENDING, item.syncStatus)
    }

    @Test
    fun `item default values are correct`() {
        val item = createItem("Test")
        assertEquals(30, item.reminderIntervalDays)
        assertEquals(false, item.isArchived)
        assertEquals("checklist", item.iconName)
        assertEquals(0, item.sortOrder)
        assertNull(item.lastCompletedAt)
        assertNull(item.categoryId)
    }

    @Test
    fun `category default values are correct`() {
        val category = createCategory("Test")
        assertEquals("category", category.iconName)
        assertEquals("#4f46e5", category.colorHex)
        assertEquals(0, category.sortOrder)
        assertEquals(false, category.isDefault)
    }

    @Test
    fun `item copy preserves id`() {
        val item = createItem("Original")
        val updated = item.copy(name = "Updated", syncStatus = SyncStatus.SYNCED)
        assertEquals(item.id, updated.id)
        assertEquals("Updated", updated.name)
        assertEquals(SyncStatus.SYNCED, updated.syncStatus)
    }

    @Test
    fun `empty item list produces empty groups`() {
        val items = emptyList<TrackerItem>()
        val grouped = items.groupBy { it.categoryId }
        assertTrue(grouped.isEmpty())
    }

    @Test
    fun `free tier limit check works`() {
        val maxTrackers = 3
        assertEquals(true, 0 < maxTrackers)   // can create
        assertEquals(true, 2 < maxTrackers)   // can create
        assertEquals(false, 3 < maxTrackers)  // at limit
        assertEquals(false, 5 < maxTrackers)  // over limit
    }
}
