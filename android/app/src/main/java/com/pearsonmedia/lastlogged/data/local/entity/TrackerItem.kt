package com.pearsonmedia.lastlogged.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracker_items")
data class TrackerItem(
    @PrimaryKey
    val id: String,

    val name: String,

    @ColumnInfo(name = "category_id")
    val categoryId: String? = null,

    @ColumnInfo(name = "reminder_interval_days")
    val reminderIntervalDays: Int = 30,

    @ColumnInfo(name = "last_completed_at")
    val lastCompletedAt: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "icon_name")
    val iconName: String = "checklist",

    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean = false,

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

enum class SyncStatus {
    SYNCED,
    PENDING,
    CONFLICT
}
