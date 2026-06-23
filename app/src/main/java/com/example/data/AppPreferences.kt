package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AppPreferences(context: Context) {
    private val sharedPreferences: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        sharedPreferences = EncryptedSharedPreferences.create(
            context,
            "secret_shared_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var geminiApiKey: String?
        get() = sharedPreferences.getString("GEMINI_API_KEY", null)
        set(value) = sharedPreferences.edit().putString("GEMINI_API_KEY", value).apply()

    var geminiModel: String
        get() = sharedPreferences.getString("GEMINI_MODEL", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        set(value) = sharedPreferences.edit().putString("GEMINI_MODEL", value).apply()

    var isGlobalTrackingEnabled: Boolean
        get() = sharedPreferences.getBoolean("GLOBAL_TRACKING", true)
        set(value) = sharedPreferences.edit().putBoolean("GLOBAL_TRACKING", value).apply()

    var telegramBotToken: String?
        get() = sharedPreferences.getString("TELEGRAM_BOT_TOKEN", null)
        set(value) = sharedPreferences.edit().putString("TELEGRAM_BOT_TOKEN", value).apply()

    var telegramBotUsername: String?
        get() = sharedPreferences.getString("TELEGRAM_BOT_USERNAME", null)
        set(value) = sharedPreferences.edit().putString("TELEGRAM_BOT_USERNAME", value).apply()
}
