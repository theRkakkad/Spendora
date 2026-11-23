package com.spendora.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fired by the deleteIntent when a pending notification (or the summary) is swiped away.
 * Android 14+ lets users dismiss "ongoing" notifications, so we put them straight back
 * for as long as the expense is still pending.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationHelper.ACTION_DISMISSED) return
        val appContext = context.applicationContext
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                NotificationHelper.refreshAll(appContext, alert = false)
            } catch (e: Exception) {
                Log.w("Spendora", "Could not re-post notifications", e)
            } finally {
                result.finish()
            }
        }
    }
}
