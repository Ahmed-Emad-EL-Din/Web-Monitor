package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "browsing_history")
data class BrowsingHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val url: String,
    val title: String?,
    val timestamp: Long = System.currentTimeMillis()
)
