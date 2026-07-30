package com.pearsonmedia.lastlogged.util

import com.pearsonmedia.lastlogged.data.local.entity.CompletionLog
import com.pearsonmedia.lastlogged.data.local.entity.TrackerCategory
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Serialises the user's local database to JSON.
 *
 * The shape deliberately matches `SettingsViewModel.exportDataLocally()` on iOS
 * and the `/export-data` edge function, so a user with both apps gets one
 * recognisable format. Key names are camelCase (matching iOS), not the
 * snake_case column names.
 *
 * Pure apart from `org.json`. The android.jar stub throws in unit tests, so
 * `org.json:json` is on the `testImplementation` classpath to supply the real
 * implementation.
 */
object DataExporter {

    /** Bump when the exported shape changes so importers can branch on it. */
    const val SCHEMA_VERSION = 1

    private fun isoFormatter(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun buildExportJson(
        categories: List<TrackerCategory>,
        items: List<TrackerItem>,
        logs: List<CompletionLog>,
        exportedAt: Long = System.currentTimeMillis()
    ): String {
        val iso = isoFormatter()

        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)
        root.put("exportedAt", iso.format(Date(exportedAt)))
        root.put("platform", "android")

        val categoryArray = JSONArray()
        for (category in categories) {
            categoryArray.put(
                JSONObject().apply {
                    put("id", category.id)
                    put("name", category.name)
                    put("iconName", category.iconName)
                    put("colorHex", category.colorHex)
                    put("sortOrder", category.sortOrder)
                    put("isDefault", category.isDefault)
                }
            )
        }
        root.put("categories", categoryArray)

        val itemArray = JSONArray()
        for (item in items) {
            itemArray.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    // JSONObject.put(String, null) removes the key, which is the
                    // behaviour we want for genuinely absent values.
                    put("categoryId", item.categoryId)
                    put("iconName", item.iconName)
                    put("sortOrder", item.sortOrder)
                    put("isArchived", item.isArchived)
                    put("createdAt", iso.format(Date(item.createdAt)))
                    put("updatedAt", iso.format(Date(item.updatedAt)))
                    put("reminderIntervalDays", item.reminderIntervalDays)
                    item.lastCompletedAt?.let { put("lastCompletedAt", iso.format(Date(it))) }
                }
            )
        }
        root.put("trackerItems", itemArray)

        val logArray = JSONArray()
        for (entry in logs) {
            logArray.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("trackerItemId", entry.trackerItemId)
                    put("completedAt", iso.format(Date(entry.completedAt)))
                    entry.notes?.let { put("notes", it) }
                }
            )
        }
        root.put("completionLogs", logArray)

        return root.toString(2)
    }

    /** e.g. `lastlogged-export-2026-07-30.json` */
    fun fileName(exportedAt: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(exportedAt))
        return "lastlogged-export-$stamp.json"
    }
}
