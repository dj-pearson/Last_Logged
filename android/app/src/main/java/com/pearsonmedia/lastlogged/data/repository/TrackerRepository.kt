package com.pearsonmedia.lastlogged.data.repository

import com.pearsonmedia.lastlogged.data.local.dao.CompletionLogDao
import com.pearsonmedia.lastlogged.data.local.dao.TrackerCategoryDao
import com.pearsonmedia.lastlogged.data.local.dao.TrackerItemDao
import com.pearsonmedia.lastlogged.data.local.entity.CompletionLog
import com.pearsonmedia.lastlogged.data.local.entity.SyncStatus
import com.pearsonmedia.lastlogged.data.local.entity.TrackerCategory
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import com.pearsonmedia.lastlogged.service.WidgetUpdater
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackerRepository @Inject constructor(
    private val trackerItemDao: TrackerItemDao,
    private val completionLogDao: CompletionLogDao,
    private val trackerCategoryDao: TrackerCategoryDao,
    private val widgetUpdater: WidgetUpdater
) {

    // --- TrackerItem ---

    fun getActiveItems(): Flow<List<TrackerItem>> = trackerItemDao.getActiveItems()

    fun getItemsByCategory(categoryId: String): Flow<List<TrackerItem>> =
        trackerItemDao.getItemsByCategory(categoryId)

    suspend fun getItemById(id: String): TrackerItem? = trackerItemDao.getItemById(id)

    suspend fun getActiveItemCount(): Int = trackerItemDao.getActiveItemCount()

    suspend fun getPendingItems(): List<TrackerItem> =
        trackerItemDao.getItemsBySyncStatus(SyncStatus.PENDING)

    suspend fun createItem(
        name: String,
        categoryId: String?,
        reminderIntervalDays: Int,
        iconName: String
    ): TrackerItem {
        val item = TrackerItem(
            id = UUID.randomUUID().toString(),
            name = name,
            categoryId = categoryId,
            reminderIntervalDays = reminderIntervalDays,
            iconName = iconName,
            syncStatus = SyncStatus.PENDING
        )
        trackerItemDao.insert(item)
        widgetUpdater.refresh()
        return item
    }

    suspend fun updateItem(item: TrackerItem) {
        trackerItemDao.update(
            item.copy(
                syncStatus = SyncStatus.PENDING,
                updatedAt = System.currentTimeMillis()
            )
        )
        widgetUpdater.refresh()
    }

    suspend fun archiveItem(id: String) {
        trackerItemDao.archiveItem(id)
        widgetUpdater.refresh()
    }

    suspend fun logCompletion(trackerItemId: String, notes: String? = null): CompletionLog =
        logCompletionAt(trackerItemId, System.currentTimeMillis(), notes)

    suspend fun logCompletionAt(
        trackerItemId: String,
        completedAt: Long,
        notes: String? = null
    ): CompletionLog {
        val log = CompletionLog(
            id = UUID.randomUUID().toString(),
            trackerItemId = trackerItemId,
            completedAt = completedAt,
            notes = notes
        )
        completionLogDao.insert(log)
        trackerItemDao.markCompleted(trackerItemId, completedAt)
        widgetUpdater.refresh()
        return log
    }

    suspend fun undoLog(logId: String, trackerItemId: String, previousCompletedAt: Long?) {
        completionLogDao.deleteById(logId)
        val item = trackerItemDao.getItemById(trackerItemId) ?: return
        trackerItemDao.update(
            item.copy(
                lastCompletedAt = previousCompletedAt,
                syncStatus = SyncStatus.PENDING,
                updatedAt = System.currentTimeMillis()
            )
        )
        widgetUpdater.refresh()
    }

    // --- CompletionLog ---

    fun getLogsForTracker(trackerItemId: String): Flow<List<CompletionLog>> =
        completionLogDao.getLogsForTracker(trackerItemId)

    fun getLogsForTrackerLimited(trackerItemId: String, limit: Int): Flow<List<CompletionLog>> =
        completionLogDao.getLogsForTrackerLimited(trackerItemId, limit)

    suspend fun getLogCountForTracker(trackerItemId: String): Int =
        completionLogDao.getLogCountForTracker(trackerItemId)

    // --- TrackerCategory ---

    fun getAllCategories(): Flow<List<TrackerCategory>> = trackerCategoryDao.getAllCategories()

    suspend fun getCategoryById(id: String): TrackerCategory? =
        trackerCategoryDao.getCategoryById(id)

    suspend fun getCategoryCount(): Int = trackerCategoryDao.getCategoryCount()

    suspend fun insertCategories(categories: List<TrackerCategory>) {
        trackerCategoryDao.insertAll(categories)
    }

    // --- Data Management ---

    /**
     * One-shot snapshot of everything stored locally, for data export.
     * Includes archived items — a data-rights export must be complete.
     */
    suspend fun exportSnapshot(): Triple<List<TrackerCategory>, List<TrackerItem>, List<CompletionLog>> =
        Triple(
            trackerCategoryDao.getAllCategoriesOnce(),
            trackerItemDao.getAllItemsOnce(),
            completionLogDao.getAllLogsOnce()
        )

    suspend fun clearAllData() {
        completionLogDao.deleteAll()
        trackerItemDao.deleteAll()
        trackerCategoryDao.deleteAll()
        widgetUpdater.refresh()
    }
}
