package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "captcha_log")
data class CaptchaLog(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val ruleId: Int,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)
