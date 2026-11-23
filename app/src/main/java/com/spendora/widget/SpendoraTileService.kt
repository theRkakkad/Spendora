package com.spendora.widget

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.spendora.ui.MainActivity
import com.spendora.util.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick settings tile that mirrors the widget's "spent today" figure and opens the app. */
class SpendoraTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_OPEN_ADD_EXPENSE, true)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch Spendora from the tile", e)
        }
    }

    override fun onDestroy() {
        refreshJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val tile = qsTile ?: return@launch
            try {
                val total = todayTotal(applicationContext)
                tile.label = LABEL
                tile.subtitle = Format.money(total)
                tile.state = Tile.STATE_ACTIVE
            } catch (e: Exception) {
                Log.w(TAG, "Could not refresh the Spendora tile", e)
                tile.label = LABEL
                tile.subtitle = "Unavailable"
                tile.state = Tile.STATE_UNAVAILABLE
            } finally {
                tile.updateTile()
            }
        }
    }

    companion object {
        private const val TAG = "SpendoraTileService"
        private const val LABEL = "Spendora"
    }
}
