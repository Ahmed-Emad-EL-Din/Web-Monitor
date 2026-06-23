package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingHistoryDao {
    @Query("SELECT * FROM tracking_history WHERE ruleId = :ruleId ORDER BY timestamp DESC")
    fun getHistoryForRule(ruleId: Int): Flow<List<TrackingHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: TrackingHistory)
    
    @Query("DELETE FROM tracking_history WHERE ruleId = :ruleId")
    suspend fun deleteHistoryForRule(ruleId: Int)
}
