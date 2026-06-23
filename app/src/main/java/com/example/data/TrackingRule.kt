package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracking_rules")
data class TrackingRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val url: String,
    val cssSelector: String?,
    val isTrackWholePage: Boolean,
    val syncFrequencyMin: Int,
    val isPremiumRule: Boolean,
    val aiConditionPrompt: String?,
    val lastKnownText: String = "",
    val isActive: Boolean = true,
    val lastChecked: Long = 0L,
    val requiresJS: Boolean = false,
    val failedChecksCount: Int = 0
)
