package com.pearsonmedia.lastlogged.data.repository

import com.pearsonmedia.lastlogged.data.local.dao.CompletionLogDao
import com.pearsonmedia.lastlogged.data.local.dao.TrackerCategoryDao
import com.pearsonmedia.lastlogged.data.local.dao.TrackerItemDao
import com.pearsonmedia.lastlogged.data.local.entity.CompletionLog
import com.pearsonmedia.lastlogged.data.local.entity.SyncStatus
import com.pearsonmedia.lastlogged.data.local.entity.TrackerCategory
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackerRepository @Inject constructor(
    private val trackerItemDao: TrackerItemDao,
    private val completionLogDao: CompletionLogDao,
    private val trackerCategoryDao: TrackerCategoryDao
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
        return item
    }

    suspend fun updateItem(item: TrackerItem) {
        trackerItemDao.update(
            item.copy(
                syncStatus = SyncStatus.PENDING,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun archiveItem(id: String) {
        trackerItemDao.archiveItem(id)
    }

    suspend fun logCompletion(trackerItemId: String, notes: String? = null): CompletionLog {
        val now = System.currentTimeMillis()
        val log = CompletionLog(
            id = UUID.randomUUID().toString(),
            trackerItemId = trackerItemId,
            completedAt = now,
            notes = notes
        )
        completionLogDao.insert(log)
        trackerItemDao.markCompleted(trackerItemId, now)
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
    }

    // --- CompletionLog ---

    fun getLogsForTracker(trackerItemId: String): Flow<List<CompletionLog>> =
        completionLogDao.getLogsForTracker(trackerItemId)

    fun getLogsForTrackerLimited(trackerItemId: String, limit: Int): Flow<List<CompletionLog>> =
        completionLogDao.getLogsForTrackerLimited(trackerItemId, limit)

    // --- TrackerCategory ---

    fun getAllCategories(): Flow<List<TrackerCategory>> = trackerCategoryDao.getAllCategories()

    suspend fun getCategoryById(id: String): TrackerCategory? =
        trackerCategoryDao.getCategoryById(id)

    suspend fun getCategoryCount(): Int = trackerCategoryDao.getCategoryCount()

    suspend fun insertCategories(categories: List<TrackerCategory>) {
        trackerCategoryDao.insertAll(categories)
    }

    // --- Data Management ---

    suspend fun clearAllData() {
        completionLogDao.deleteAll()
        trackerItemDao.deleteAll()
        trackerCategoryDao.deleteAll()
    }
}
