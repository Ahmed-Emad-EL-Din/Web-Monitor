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

class TrackingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ruleId = inputData.getInt("RULE_ID", -1)
        if (ruleId == -1) return@withContext Result.failure()

        val db = AppDatabase.getDatabase(applicationContext)
        val rule = db.trackingRuleDao().getRuleById(ruleId) ?: return@withContext Result.failure()

        if (!rule.isActive) return@withContext Result.success()

        val updatedRule = rule.copy(lastChecked = System.currentTimeMillis())
        db.trackingRuleDao().updateRule(updatedRule)

        try {
            var htmlBody = ""
            if (rule.requiresJS) {
                htmlBody = fetchWithWebView(rule.url)
            } else {
                htmlBody = fetchWithOkHttp(rule.url) ?: ""
            }

            if (htmlBody.isEmpty()) {
                return@withContext handleBrokenRule(rule, db)
            }

            val doc = Jsoup.parse(htmlBody)
            var newTextRaw = ""
            
            if (rule.isTrackWholePage) {
                newTextRaw = doc.text()
            } else {
                val element = doc.select(rule.cssSelector ?: "").firstOrNull()
                if (element == null) {
                    return@withContext handleBrokenRule(rule, db)
                }
                newTextRaw = element.text() ?: ""
            }

            // Normal successful logic
            if (rule.failedChecksCount > 0) {
                db.trackingRuleDao().updateRule(rule.copy(failedChecksCount = 0))
            }

            val newText = Jsoup.parse(newTextRaw).text()
            val oldText = Jsoup.parse(rule.lastKnownText).text()

            if (newText == oldText) {
                return@withContext Result.success() // STRICT GATE: DO NOTHING
            }

            var aiSummaryOutput: String? = null

            if (!rule.isPremiumRule) {
                val notificationText = "Update: Changed from \n\n${oldText.take(50)}...\n\n to \n\n${newText.take(50)}...\n\n"
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
                        aiSummaryOutput = summary
                        sendNotification(ruleId, "AI Alert: Rule ${rule.id}", summary)
                    }
                }
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

            Result.success()
        } catch (e: Exception) {
            Log.e("TrackingWorker", "Exception in worker: ", e)
            Result.retry()
        }
    }

    private suspend fun handleBrokenRule(rule: TrackingRule, db: AppDatabase): Result {
        val fails = rule.failedChecksCount + 1
        if (fails >= 3) {
            db.trackingRuleDao().updateRule(rule.copy(failedChecksCount = fails, isActive = false))
            sendNotification(rule.id, "Tracker Broken: ${rule.url}", "Layout changed. Tap to re-select.")
            return Result.failure()
        } else {
            db.trackingRuleDao().updateRule(rule.copy(failedChecksCount = fails))
            return Result.retry()
        }
    }

    private fun fetchWithOkHttp(url: String): String? {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .cookieJar(WebViewCookieJar())
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        return response.body?.string()
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
}
