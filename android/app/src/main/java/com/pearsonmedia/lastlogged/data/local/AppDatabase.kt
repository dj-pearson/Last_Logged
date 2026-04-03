package com.pearsonmedia.lastlogged.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pearsonmedia.lastlogged.data.local.converter.Converters
import com.pearsonmedia.lastlogged.data.local.dao.CompletionLogDao
import com.pearsonmedia.lastlogged.data.local.dao.TrackerCategoryDao
import com.pearsonmedia.lastlogged.data.local.dao.TrackerItemDao
import com.pearsonmedia.lastlogged.data.local.entity.CompletionLog
import com.pearsonmedia.lastlogged.data.local.entity.TrackerCategory
import com.pearsonmedia.lastlogged.data.local.entity.TrackerItem

@Database(
    entities = [
        TrackerItem::class,
        CompletionLog::class,
        TrackerCategory::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackerItemDao(): TrackerItemDao
    abstract fun completionLogDao(): CompletionLogDao
    abstract fun trackerCategoryDao(): TrackerCategoryDao
}
