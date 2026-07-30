package com.pearsonmedia.lastlogged

import com.pearsonmedia.lastlogged.data.local.entity.CompletionLog
import com.pearsonmedia.lastlogged.data.local.entity.TrackerCategory
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import com.pearsonmedia.lastlogged.util.DataExporter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataExporterTest {

    private val exportedAt = 1_700_000_000_000L

    private val category = TrackerCategory(
        id = "cat-1",
        name = "Home",
        iconName = "home",
        colorHex = "#4f46e5",
        sortOrder = 1,
        isDefault = true
    )

    private val item = TrackerItem(
        id = "item-1",
        name = "HVAC Filter",
        categoryId = "cat-1",
        reminderIntervalDays = 90,
        lastCompletedAt = exportedAt - 86_400_000L,
        createdAt = exportedAt - 10 * 86_400_000L,
        sortOrder = 2,
        iconName = "air",
        isArchived = false,
        updatedAt = exportedAt
    )

    private val log = CompletionLog(
        id = "log-1",
        trackerItemId = "item-1",
        completedAt = exportedAt - 86_400_000L,
        notes = "Replaced with a MERV 13"
    )

    private fun export(
        categories: List<TrackerCategory> = listOf(category),
        items: List<TrackerItem> = listOf(item),
        logs: List<CompletionLog> = listOf(log)
    ) = JSONObject(DataExporter.buildExportJson(categories, items, logs, exportedAt))

    @Test
    fun `export contains all three collections`() {
        val json = export()

        assertEquals(1, json.getJSONArray("categories").length())
        assertEquals(1, json.getJSONArray("trackerItems").length())
        assertEquals(1, json.getJSONArray("completionLogs").length())
    }

    @Test
    fun `export carries schema version and timestamp metadata`() {
        val json = export()

        assertEquals(DataExporter.SCHEMA_VERSION, json.getInt("schemaVersion"))
        assertEquals("android", json.getString("platform"))
        assertEquals("2023-11-14T22:13:20.000Z", json.getString("exportedAt"))
    }

    @Test
    fun `category fields round-trip`() {
        val exported = export().getJSONArray("categories").getJSONObject(0)

        assertEquals("cat-1", exported.getString("id"))
        assertEquals("Home", exported.getString("name"))
        assertEquals("home", exported.getString("iconName"))
        assertEquals("#4f46e5", exported.getString("colorHex"))
        assertEquals(1, exported.getInt("sortOrder"))
        assertTrue(exported.getBoolean("isDefault"))
    }

    @Test
    fun `tracker item fields round-trip with ISO timestamps`() {
        val exported = export().getJSONArray("trackerItems").getJSONObject(0)

        assertEquals("item-1", exported.getString("id"))
        assertEquals("HVAC Filter", exported.getString("name"))
        assertEquals("cat-1", exported.getString("categoryId"))
        assertEquals(90, exported.getInt("reminderIntervalDays"))
        assertFalse(exported.getBoolean("isArchived"))
        assertEquals("2023-11-13T22:13:20.000Z", exported.getString("lastCompletedAt"))
        assertEquals("2023-11-04T22:13:20.000Z", exported.getString("createdAt"))
    }

    @Test
    fun `completion log fields round-trip`() {
        val exported = export().getJSONArray("completionLogs").getJSONObject(0)

        assertEquals("log-1", exported.getString("id"))
        assertEquals("item-1", exported.getString("trackerItemId"))
        assertEquals("2023-11-13T22:13:20.000Z", exported.getString("completedAt"))
        assertEquals("Replaced with a MERV 13", exported.getString("notes"))
    }

    @Test
    fun `null optional fields are omitted rather than exported as the string null`() {
        val bare = item.copy(id = "item-2", categoryId = null, lastCompletedAt = null)
        val bareLog = log.copy(id = "log-2", notes = null)

        val json = export(items = listOf(bare), logs = listOf(bareLog))
        val exportedItem = json.getJSONArray("trackerItems").getJSONObject(0)
        val exportedLog = json.getJSONArray("completionLogs").getJSONObject(0)

        assertFalse(exportedItem.has("categoryId"))
        assertFalse(exportedItem.has("lastCompletedAt"))
        assertFalse(exportedLog.has("notes"))
    }

    @Test
    fun `archived items are included because an export must be complete`() {
        val archived = item.copy(id = "item-archived", isArchived = true)

        val json = export(items = listOf(item, archived))

        assertEquals(2, json.getJSONArray("trackerItems").length())
    }

    @Test
    fun `empty database still produces a valid document`() {
        val json = export(categories = emptyList(), items = emptyList(), logs = emptyList())

        assertEquals(0, json.getJSONArray("categories").length())
        assertEquals(0, json.getJSONArray("trackerItems").length())
        assertEquals(0, json.getJSONArray("completionLogs").length())
        assertEquals(DataExporter.SCHEMA_VERSION, json.getInt("schemaVersion"))
    }

    @Test
    fun `file name is dated`() {
        assertEquals("lastlogged-export-2023-11-14.json", DataExporter.fileName(exportedAt))
    }
}
