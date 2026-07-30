package com.pearsonmedia.lastlogged.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pearsonmedia.lastlogged.data.local.entity.CompletionLog
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletionLogDao {

    @Query("SELECT * FROM completion_logs WHERE tracker_item_id = :trackerItemId ORDER BY completed_at DESC")
    fun getLogsForTracker(trackerItemId: String): Flow<List<CompletionLog>>

    @Query("SELECT * FROM completion_logs WHERE tracker_item_id = :trackerItemId ORDER BY completed_at DESC LIMIT :limit")
    fun getLogsForTrackerLimited(trackerItemId: String, limit: Int): Flow<List<CompletionLog>>

    @Query("SELECT * FROM completion_logs WHERE id = :id")
    suspend fun getLogById(id: String): CompletionLog?

    @Query("SELECT COUNT(*) FROM completion_logs WHERE tracker_item_id = :trackerItemId")
    suspend fun getLogCountForTracker(trackerItemId: String): Int

    /** One-shot snapshot for data export. */
    @Query("SELECT * FROM completion_logs ORDER BY completed_at DESC")
    suspend fun getAllLogsOnce(): List<CompletionLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: CompletionLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<CompletionLog>)

    @Query("DELETE FROM completion_logs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM completion_logs WHERE tracker_item_id = :trackerItemId")
    suspend fun deleteLogsForTracker(trackerItemId: String)

    @Query("DELETE FROM completion_logs")
    suspend fun deleteAll()
}
