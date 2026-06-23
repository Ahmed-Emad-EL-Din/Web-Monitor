package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.AppDatabase
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    @OptIn(DelicateCoroutinesApi::class)
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val db = AppDatabase.getDatabase(context)
            
            GlobalScope.launch(Dispatchers.IO) {
                val rules = db.trackingRuleDao().getAllRules().firstOrNull() ?: emptyList()
                val workManager = WorkManager.getInstance(context)
                
                rules.filter { it.isActive }.forEach { rule ->
                    val workRequest = PeriodicWorkRequestBuilder<TrackingWorker>(
                        rule.syncFrequencyMin.toLong(), TimeUnit.MINUTES
                    )
                    .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                    .setInputData(androidx.work.workDataOf("RULE_ID" to rule.id))
                    .build()
                    
                    workManager.enqueueUniquePeriodicWork(
                        "track_${rule.id}",
                        ExistingPeriodicWorkPolicy.UPDATE,
                        workRequest
                    )
                }
            }
        }
    }
}
