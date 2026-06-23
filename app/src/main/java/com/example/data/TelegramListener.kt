package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "telegram_listeners")
data class TelegramListener(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val chatId: Long,
    val listenerName: String
)
