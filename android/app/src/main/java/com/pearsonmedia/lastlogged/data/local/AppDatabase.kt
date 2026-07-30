package com.pearsonmedia.lastlogged.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
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

    companion object {
        const val NAME = "lastlogged.db"

        /**
         * Every schema change must add a [Migration] here and bump [Database.version].
         *
         * We deliberately never call `fallbackToDestructiveMigration()` — a user's
         * tracker history is the whole point of the app, and pending-sync rows that
         * have not reached Supabase yet would be lost forever. A missing migration
         * should crash loudly in QA rather than silently wipe production data.
         *
         * When adding one: bump the version, append the migration, run a debug build
         * so Room re-exports `app/schemas/…/<version>.json`, commit that JSON, and
         * add a case to `AppDatabaseMigrationTest`.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}
