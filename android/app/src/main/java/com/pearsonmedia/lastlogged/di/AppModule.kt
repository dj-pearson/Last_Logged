package com.pearsonmedia.lastlogged.di

import android.content.Context
import androidx.room.Room
import com.pearsonmedia.lastlogged.data.local.AppDatabase
import com.pearsonmedia.lastlogged.data.local.dao.CompletionLogDao
import com.pearsonmedia.lastlogged.data.local.dao.TrackerCategoryDao
import com.pearsonmedia.lastlogged.data.local.dao.TrackerItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "lastlogged.db"
        ).build()
    }

    @Provides
    fun provideTrackerItemDao(db: AppDatabase): TrackerItemDao = db.trackerItemDao()

    @Provides
    fun provideCompletionLogDao(db: AppDatabase): CompletionLogDao = db.completionLogDao()

    @Provides
    fun provideTrackerCategoryDao(db: AppDatabase): TrackerCategoryDao = db.trackerCategoryDao()
}
