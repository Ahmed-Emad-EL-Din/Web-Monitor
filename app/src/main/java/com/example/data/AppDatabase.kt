package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TrackingRule::class, TrackingHistory::class, BrowsingHistory::class, CaptchaLog::class, TelegramListener::class, RuleListenerCrossRef::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackingRuleDao(): TrackingRuleDao
    abstract fun trackingHistoryDao(): TrackingHistoryDao
    abstract fun browsingHistoryDao(): BrowsingHistoryDao
    abstract fun captchaLogDao(): CaptchaLogDao
    abstract fun telegramListenerDao(): TelegramListenerDao
    abstract fun ruleListenerCrossRefDao(): RuleListenerCrossRefDao

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
