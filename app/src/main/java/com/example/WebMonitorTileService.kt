package com.example

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.AppDatabase
import com.example.data.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class WebMonitorTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = AppPreferences(applicationContext)
        val isEnabled = prefs.isGlobalTrackingEnabled
        val newState = !isEnabled
        prefs.isGlobalTrackingEnabled = newState
        updateTileState()
        
        GlobalScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val rules = db.trackingRuleDao().getAllRules().firstOrNull() ?: emptyList()
            val workManager = WorkManager.getInstance(applicationContext)
            
            if (newState) {
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
            } else {
                rules.forEach { rule ->
                    workManager.cancelUniqueWork("track_${rule.id}")
                }
            }
        }
    }

    private fun updateTileState() {
        val prefs = AppPreferences(applicationContext)
        val tile = qsTile
        if (tile != null) {
            if (prefs.isGlobalTrackingEnabled) {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Web Monitor"
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Paused"
            }
            tile.updateTile()
        }
    }
}
