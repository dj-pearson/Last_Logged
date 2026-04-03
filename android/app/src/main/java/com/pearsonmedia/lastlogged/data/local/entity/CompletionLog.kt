package com.pearsonmedia.lastlogged.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "completion_logs",
    foreignKeys = [
        ForeignKey(
            entity = TrackerItem::class,
            parentColumns = ["id"],
            childColumns = ["tracker_item_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tracker_item_id")]
)
data class CompletionLog(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "tracker_item_id")
    val trackerItemId: String,

    @ColumnInfo(name = "completed_at")
    val completedAt: Long = System.currentTimeMillis(),

    val notes: String? = null
)
