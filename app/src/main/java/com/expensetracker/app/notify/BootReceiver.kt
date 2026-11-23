package com.expensetracker.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.expensetracker.app.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Restores pending notifications and the periodic reminder after a reboot or app update. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val appContext = context.applicationContext
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val minutes = appContext.app.settings.snapshot().reminderMinutes
                ReminderScheduler.schedule(appContext, minutes, replace = false)
                NotificationHelper.refreshAll(appContext, alert = false)
            } catch (e: Exception) {
                Log.w("ExpenseTracker", "Boot restore failed", e)
            } finally {
                result.finish()
            }
        }
    }
}
