package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingRuleDao {
    @Query("SELECT * FROM tracking_rules")
    fun getAllRules(): Flow<List<TrackingRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: TrackingRule): Long

    @Query("SELECT * FROM tracking_rules WHERE id = :id")
    suspend fun getRuleById(id: Int): TrackingRule?

    @Query("UPDATE tracking_rules SET lastKnownText = :newText WHERE id = :id")
    suspend fun updateRuleText(id: Int, newText: String)

    @androidx.room.Delete
    suspend fun deleteRule(rule: TrackingRule)

    @androidx.room.Update
    suspend fun updateRule(rule: TrackingRule)
}
