package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowsingHistoryDao {
    @Query("SELECT * FROM browsing_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<BrowsingHistory>>

    @Insert
    suspend fun insert(history: BrowsingHistory)
    
    @Query("DELETE FROM browsing_history")
    suspend fun clearHistory()
}
