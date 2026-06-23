package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleListenerCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(crossRef: RuleListenerCrossRef)

    @Delete
    suspend fun delete(crossRef: RuleListenerCrossRef)

    @Query("SELECT listenerId FROM rule_listener_cross_ref WHERE ruleId = :ruleId")
    suspend fun getListenersForRule(ruleId: Int): List<Int>

    @Query("SELECT ruleId FROM rule_listener_cross_ref WHERE listenerId = :listenerId")
    suspend fun getRulesForListener(listenerId: Int): List<Int>

    @Query("SELECT ruleId FROM rule_listener_cross_ref WHERE listenerId = :listenerId")
    fun getRulesForListenerFlow(listenerId: Int): Flow<List<Int>>
}
