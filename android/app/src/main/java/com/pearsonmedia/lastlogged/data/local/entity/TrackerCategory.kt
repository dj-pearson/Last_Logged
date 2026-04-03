package com.pearsonmedia.lastlogged.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracker_categories")
data class TrackerCategory(
    @PrimaryKey
    val id: String,

    val name: String,

    @ColumnInfo(name = "icon_name")
    val iconName: String = "category",

    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#4f46e5",

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "is_default")
    val isDefault: Boolean = false
)
