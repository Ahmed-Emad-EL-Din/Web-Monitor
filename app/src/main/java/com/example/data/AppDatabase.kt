package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TrackingRule::class, TrackingHistory::class, BrowsingHistory::class, CaptchaLog::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackingRuleDao(): TrackingRuleDao
    abstract fun trackingHistoryDao(): TrackingHistoryDao
    abstract fun browsingHistoryDao(): BrowsingHistoryDao
    abstract fun captchaLogDao(): CaptchaLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "web_monitor_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
