package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptchaLogDao {
    @Query("SELECT * FROM captcha_log ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<CaptchaLog>>

    @Insert
    suspend fun insert(log: CaptchaLog)
    
    @Query("DELETE FROM captcha_log WHERE ruleId = :ruleId")
    suspend fun clearLogForRule(ruleId: Int)
}
