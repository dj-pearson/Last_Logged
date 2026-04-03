package com.pearsonmedia.lastlogged.data.local.converter

import androidx.room.TypeConverter
import com.pearsonmedia.lastlogged.data.local.entity.SyncStatus

class Converters {
    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}
