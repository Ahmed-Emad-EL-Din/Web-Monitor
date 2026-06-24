package com.example

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.AppPreferences
import com.example.data.TrackingHistory
import com.example.data.TrackingRule
import com.example.network.WebViewCookieJar
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class TrackingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ruleId = inputData.getInt("RULE_ID", -1)
        if (ruleId == -1) return@withContext Result.failure()

        val db = AppDatabase.getDatabase(applicationContext)
        val rule = db.trackingRuleDao().getRuleById(ruleId) ?: return@withContext Result.failure()

        val appPrefs = AppPreferences(applicationContext)
        if (!appPrefs.isGlobalTrackingEnabled) return@withContext Result.success()

        val isManualSync = inputData.getBoolean("IS_MANUAL_SYNC", false)
        if (!rule.isActive && !isManualSync) return@withContext Result.success()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "web_monitor_fg",
                "Background Tasks",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(applicationContext, "web_monitor_fg")
            .setContentTitle("Web Monitor")
            .setContentText("Checking URLs in background...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
            
        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            androidx.work.ForegroundInfo(101, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            androidx.work.ForegroundInfo(101, notification)
        }
        
        try {
            setForeground(foregroundInfo)
        } catch (e: Exception) {}

        val currentTime = System.currentTimeMillis()
        var updatedRule = rule.copy(lastChecked = currentTime)
        db.trackingRuleDao().updateRule(updatedRule)

        try {
            var htmlBody = ""
            if (updatedRule.requiresJS) {
                htmlBody = fetchWithWebView(updatedRule.url)
            } else {
                htmlBody = fetchWithOkHttp(updatedRule.url, updatedRule, db) ?: ""
            }

            if (htmlBody.isEmpty()) {
                return@withContext handleBrokenRule(updatedRule, db, "Failed to load page content. Server may be blocking the request.")
            }

            val doc = Jsoup.parse(htmlBody)
            var newTextRaw = ""
            
            if (updatedRule.isTrackWholePage) {
                newTextRaw = doc.text()
            } else {
                val element = doc.select(updatedRule.cssSelector ?: "").firstOrNull()
                if (element == null) {
                    return@withContext handleBrokenRule(updatedRule, db, "Layout changed. Element not found. Tap to re-select.")
                }
                newTextRaw = element.text() ?: ""
            }

            // Normal successful logic
            if (updatedRule.failedChecksCount > 0) {
                updatedRule = updatedRule.copy(failedChecksCount = 0)
                db.trackingRuleDao().updateRule(updatedRule)
            }

            val newText = Jsoup.parse(newTextRaw).text()
            val oldText = Jsoup.parse(updatedRule.lastKnownText).text()

            if (newText == oldText) {
                val outputData = androidx.work.workDataOf("HAS_CHANGES" to false)
                return@withContext Result.success(outputData) // STRICT GATE: DO NOTHING
            }

            var aiSummaryOutput: String? = null
            var sentAiNotification = false

            if (updatedRule.isPremiumRule) {
                val appPrefs = AppPreferences(applicationContext)
                val apiKey = appPrefs.geminiApiKey
                if (!apiKey.isNullOrBlank()) {
                    val modelName = appPrefs.geminiModel
                    val generativeModel = GenerativeModel(
                        modelName = modelName,
                        apiKey = apiKey
                    )
                    
                    val aiPrompt = if (!updatedRule.aiConditionPrompt.isNullOrBlank()) {
                        "Old Content: '${oldText}'. New Content: '${newText}'. Condition: '${updatedRule.aiConditionPrompt}'. If condition NOT met, reply 'IGNORE'. If met, reply 'TRIGGER: \n\n$$ 1-sentence summary of change $$\\n\\n'."
                    } else {
                        "Old Content: '${oldText}'. New Content: '${newText}'. Please provide a 1-sentence summary of the changes between the old and new content. Reply with 'TRIGGER: \n\n$$ your 1-sentence summary $$\\n\\n'."
                    }
                    
                    try {
                        val aiResponse = generativeModel.generateContent(aiPrompt)
                        val reply = aiResponse.text?.trim() ?: "IGNORE"
                        if (reply.startsWith("TRIGGER")) {
                            val summary = reply.removePrefix("TRIGGER:").replace("$$", "").trim()
                            aiSummaryOutput = summary
                            
                            val domainName = try { java.net.URL(rule.url).host.removePrefix("www.") } catch(e: Exception) { "Website" }
                            val title = "Monitor Alert: $domainName"
                            
                            sendNotification(ruleId, title, summary)
                            sendTelegramNotifications(ruleId, "🔔 [$title]\n\n$summary", db)
                            sentAiNotification = true
                        } else if (reply == "IGNORE") {
                            sentAiNotification = true // AI deliberately ignored, so don't send default
                        }
                    } catch (e: Exception) {
                        Log.e("TrackingWorker", "AI generation failed", e)
                    }
                }
            }

            if (!updatedRule.isPremiumRule || (!sentAiNotification && aiSummaryOutput == null)) {
                val domainName = try { java.net.URL(rule.url).host.removePrefix("www.") } catch(e: Exception) { "Website" }
                val title = "Monitor Alert: $domainName"
                val notificationText = "Content updated from:\n${oldText.take(50)}...\n\nTo:\n${newText.take(50)}..."
                sendNotification(ruleId, title, notificationText)
                sendTelegramNotifications(ruleId, "🔔 [$title]\n\n$notificationText", db)
            }

            db.trackingRuleDao().updateRuleText(ruleId, newTextRaw)
            
            // Insert history
            val history = TrackingHistory(
                ruleId = ruleId,
                timestamp = System.currentTimeMillis(),
                oldText = oldText,
                newText = newText,
                aiSummary = aiSummaryOutput
            )
            db.trackingHistoryDao().insertHistory(history)

            val outputData = androidx.work.workDataOf("HAS_CHANGES" to true)
            Result.success(outputData)
        } catch (e: Exception) {
            Log.e("TrackingWorker", "Exception in worker: ", e)
            Result.retry()
        }
    }

    private suspend fun handleBrokenRule(rule: TrackingRule, db: AppDatabase, errorReason: String = "Layout changed. Tap to re-select."): Result {
        val fails = rule.failedChecksCount + 1
        if (fails >= 3) {
            val domainName = try { java.net.URL(rule.url).host.removePrefix("www.") } catch(e: Exception) { "Website" }
            db.trackingRuleDao().updateRule(rule.copy(failedChecksCount = fails, isActive = false))
            sendNotification(rule.id, "Tracker Broken: $domainName", errorReason)
            sendTelegramNotifications(rule.id, "❌ [Tracker Broken: $domainName]\n\n$errorReason", db)
            return Result.failure()
        } else {
            db.trackingRuleDao().updateRule(rule.copy(failedChecksCount = fails))
            return Result.retry()
        }
    }

    private suspend fun fetchWithOkHttp(url: String, rule: TrackingRule, db: AppDatabase): String? {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .cookieJar(WebViewCookieJar())
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .build()
        
        val response = client.newCall(request).execute()
        
        if (response.code == 419 || response.code == 409) {
            val fails = rule.failedChecksCount + 1
            if (fails >= 3) {
                 val domainName = try { java.net.URL(rule.url).host.removePrefix("www.") } catch(e: Exception) { "Website" }
                 db.trackingRuleDao().updateRule(rule.copy(failedChecksCount = fails, isActive = false))
                 sendNotification(rule.id, "Session Expired: $domainName", "Please log in to $domainName to resume tracking.")
                 sendTelegramNotifications(rule.id, "⚠️ [Session Expired: $domainName]\n\nPlease log in to the website to resume tracking.", db)
                 return null
            } else {
                 db.trackingRuleDao().updateRule(rule.copy(failedChecksCount = fails))
                 return fetchWithWebView(url)
            }
        }

        if (!response.isSuccessful) return null
        return response.body?.string()
    }
    
    private fun extractBaseUrl(url: String): String {
        try {
            val configUrl = java.net.URL(url)
            return "${configUrl.protocol}://${configUrl.host}"
        } catch (e: Exception) {
            return url
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchWithWebView(url: String): String = withContext(Dispatchers.Main) {
        suspendCoroutine { continuation ->
            val webView = WebView(applicationContext)
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
                        delay(2000) // wait an additional 2000ms for frameworks to render
                        webView.evaluateJavascript(
                            "(function() { return document.documentElement.outerHTML; })();"
                        ) { html ->
                            var cleanHtml = html?.replace("\\u003C", "<")
                                ?.replace("\\\"", "\"")
                                ?.let { if (it.startsWith("\"") && it.endsWith("\"")) it.substring(1, it.length - 1) else it }
                                ?: ""
                            webView.destroy()
                            continuation.resume(cleanHtml)
                        }
                    }
                }
            }
            webView.loadUrl(url)
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

    private suspend fun sendTelegramNotifications(ruleId: Int, message: String, db: AppDatabase) {
        val appPrefs = AppPreferences(applicationContext)
        val token = appPrefs.telegramBotToken
        if (token.isNullOrBlank()) return

        val listenerIds = db.ruleListenerCrossRefDao().getListenersForRule(ruleId)
        if (listenerIds.isEmpty()) return

        val listeners = db.telegramListenerDao().getAllListenersList().filter { it.id in listenerIds }

        val client = OkHttpClient()
        val mediaType = "application/json".toMediaType()
        for (listener in listeners) {
            val json = org.json.JSONObject().apply {
                put("chat_id", listener.chatId)
                put("text", message)
            }
            val body = json.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://api.telegram.org/bot$token/sendMessage")
                .post(body)
                .build()

            try {
                client.newCall(request).execute().use { }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
