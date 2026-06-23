package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.AppPreferences
import com.example.network.WebViewCookieJar
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class TrackingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ruleId = inputData.getInt("RULE_ID", -1)
        if (ruleId == -1) return@withContext Result.failure()

        val db = AppDatabase.getDatabase(applicationContext)
        val rule = db.trackingRuleDao().getRuleById(ruleId) ?: return@withContext Result.failure()

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .cookieJar(WebViewCookieJar())
                .build()

            val request = Request.Builder()
                .url(rule.url)
                // Try simulating a normal browser to avoid simple blocks
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("TrackingWorker", "HTTP Error: ${response.code}")
                return@withContext Result.retry()
            }

            val htmlBody = response.body?.string() ?: return@withContext Result.failure()
            val doc = Jsoup.parse(htmlBody)
            
            var newTextRaw = ""
            if (rule.isTrackWholePage) {
                newTextRaw = doc.text()
            } else {
                val element = doc.select(rule.cssSelector ?: "").firstOrNull()
                newTextRaw = element?.text() ?: ""
            }

            // Clean text formatting to avoid invisible HTML formatting false-positives
            val newText = Jsoup.parse(newTextRaw).text()
            val oldText = Jsoup.parse(rule.lastKnownText).text()

            if (newText == oldText) {
                return@withContext Result.success() // STRICT GATE: DO NOTHING
            }

            if (!rule.isPremiumRule) {
                val notificationText = "Update: Changed from \n\n${oldText}\n\n to \n\n${newText}\n\n"
                sendNotification(ruleId, "Rule ${rule.id} Updated", notificationText)
            } else {
                val appPrefs = AppPreferences(applicationContext)
                val apiKey = appPrefs.geminiApiKey
                if (!apiKey.isNullOrBlank()) {
                    val modelName = appPrefs.geminiModel
                    val generativeModel = GenerativeModel(
                        modelName = modelName,
                        apiKey = apiKey
                    )
                    
                    val aiPrompt = "Old Content: '${oldText}'. New Content: '${newText}'. Condition: '${rule.aiConditionPrompt}'. If condition NOT met, reply 'IGNORE'. If met, reply 'TRIGGER: \n\n$$ 1-sentence summary of change $$\\n\\n'."
                    
                    val aiResponse = generativeModel.generateContent(aiPrompt)
                    val reply = aiResponse.text?.trim() ?: "IGNORE"
                    if (reply.startsWith("TRIGGER")) {
                        val summary = reply.removePrefix("TRIGGER:").replace("$$", "").trim()
                        sendNotification(ruleId, "AI Alert: Rule ${rule.id}", summary)
                    }
                }
            }

            // Update local DB
            db.trackingRuleDao().updateRuleText(ruleId, newTextRaw)

            Result.success()
        } catch (e: Exception) {
            Log.e("TrackingWorker", "Exception in worker: ", e)
            Result.retry()
        }
    }

    private fun sendNotification(ruleId: Int, title: String, content: String) {
        val channelId = "web_monitor_channel"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Web Monitor Updates",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("EXTRA_NAVIGATE_TO", "CHANGE_DETAILS")
            putExtra("EXTRA_RULE_ID", ruleId)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            ruleId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(ruleId, notification)
    }
}
