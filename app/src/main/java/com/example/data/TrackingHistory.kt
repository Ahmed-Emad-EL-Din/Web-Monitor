package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracking_history")
data class TrackingHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ruleId: Int,
    val timestamp: Long,
    val oldText: String,
    val newText: String,
    val aiSummary: String?
)
