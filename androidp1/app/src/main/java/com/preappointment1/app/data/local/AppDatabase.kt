package com.preappointment1.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FollowUpEntity::class,
        TimelineEventEntity::class,
        CachedReportEntity::class,
        LocalDocumentEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun followUpDao(): FollowUpDao
    abstract fun timelineEventDao(): TimelineEventDao
    abstract fun cachedReportDao(): CachedReportDao
    abstract fun localDocumentDao(): LocalDocumentDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "p1_local.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
