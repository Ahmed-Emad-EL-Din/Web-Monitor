package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracking_rules")
data class TrackingRule(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val url: String,
    val cssSelector: String?,
    val lastKnownText: String,
    val isTrackWholePage: Boolean,
    val isPremiumRule: Boolean,
    val aiConditionPrompt: String?,
    val syncFrequencyMin: Int = 15 // minutes
)
