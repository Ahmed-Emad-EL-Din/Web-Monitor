package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TelegramListenerDao {
    @Query("SELECT * FROM telegram_listeners")
    fun getAllListeners(): Flow<List<TelegramListener>>

    @Query("SELECT * FROM telegram_listeners")
    suspend fun getAllListenersList(): List<TelegramListener>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(listener: TelegramListener)

    @Delete
    suspend fun delete(listener: TelegramListener)
}
