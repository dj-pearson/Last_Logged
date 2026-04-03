package com.pearsonmedia.lastlogged.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pearsonmedia.lastlogged.data.local.entity.SyncStatus
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerItemDao {

    @Query("SELECT * FROM tracker_items WHERE is_archived = 0 ORDER BY sort_order ASC")
    fun getActiveItems(): Flow<List<TrackerItem>>

    @Query("SELECT * FROM tracker_items WHERE is_archived = 0 AND category_id = :categoryId ORDER BY sort_order ASC")
    fun getItemsByCategory(categoryId: String): Flow<List<TrackerItem>>

    @Query("SELECT * FROM tracker_items WHERE id = :id")
    suspend fun getItemById(id: String): TrackerItem?

    @Query("SELECT * FROM tracker_items WHERE sync_status = :status")
    suspend fun getItemsBySyncStatus(status: SyncStatus): List<TrackerItem>

    @Query("SELECT COUNT(*) FROM tracker_items WHERE is_archived = 0")
    suspend fun getActiveItemCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TrackerItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TrackerItem>)

    @Update
    suspend fun update(item: TrackerItem)

    @Query("UPDATE tracker_items SET is_archived = 1, sync_status = 'PENDING', updated_at = :now WHERE id = :id")
    suspend fun archiveItem(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE tracker_items SET last_completed_at = :completedAt, sync_status = 'PENDING', updated_at = :now WHERE id = :id")
    suspend fun markCompleted(id: String, completedAt: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM tracker_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tracker_items")
    suspend fun deleteAll()
}
