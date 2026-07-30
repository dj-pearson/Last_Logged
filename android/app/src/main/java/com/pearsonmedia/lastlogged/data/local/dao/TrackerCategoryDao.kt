package com.pearsonmedia.lastlogged.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pearsonmedia.lastlogged.data.local.entity.TrackerCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerCategoryDao {

    @Query("SELECT * FROM tracker_categories ORDER BY sort_order ASC")
    fun getAllCategories(): Flow<List<TrackerCategory>>

    @Query("SELECT * FROM tracker_categories WHERE id = :id")
    suspend fun getCategoryById(id: String): TrackerCategory?

    @Query("SELECT COUNT(*) FROM tracker_categories")
    suspend fun getCategoryCount(): Int

    /** One-shot snapshot for data export. */
    @Query("SELECT * FROM tracker_categories ORDER BY sort_order ASC")
    suspend fun getAllCategoriesOnce(): List<TrackerCategory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: TrackerCategory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<TrackerCategory>)

    @Query("DELETE FROM tracker_categories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tracker_categories")
    suspend fun deleteAll()
}
