package com.spendora.app.sms

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.spendora.app.app
import com.spendora.app.data.Expense
import com.spendora.app.notify.NotificationHelper
import com.spendora.app.notify.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The whole detection flow for one SMS: parse -> dedupe -> store as PENDING ->
 * persistent notification -> instant popup. Used by the real receiver and by "Test popup".
 */
object SmsProcessor {

    enum class Outcome { DETECTED, IGNORED, DUPLICATE }

    suspend fun process(context: Context, sender: String, body: String, timestamp: Long): Outcome {
        val appContext = context.applicationContext
        val app = appContext.app
        val settings = app.settings.snapshot()

        val parsed = SmsParser.parse(sender, body, settings.senders, settings.keywords)
            ?: return Outcome.IGNORED

        val expense = Expense(
            amount = parsed.amount,
            label = "",
            timestamp = timestamp,
            sender = sender,
            upiRef = parsed.upiRef,
            userName = settings.userName,
            status = Expense.STATUS_PENDING,
            isManual = false,
            dedupeKey = SmsParser.dedupeKey(parsed.amount, timestamp, sender, parsed.upiRef)
        )
        val id = app.repository.insert(expense)
        if (id <= 0L) return Outcome.DUPLICATE
        val saved = expense.copy(id = id)

        // Persistent reminder. The full-screen intent opens the popup over the lock screen
        // and shows a heads-up when the overlay permission is missing.
        // Each alert is independent: a notification or scheduler failure must not
        // prevent the popup after the payment has already been stored.
        try {
            NotificationHelper.postPending(appContext, saved, alert = true, fullScreen = true)
        } catch (e: Exception) {
            Log.w("Spendora", "Could not post payment notification", e)
        }
        try {
            NotificationHelper.updateSummary(appContext)
        } catch (e: Exception) {
            Log.w("Spendora", "Could not update pending payment summary", e)
        }
        try {
            ReminderScheduler.schedule(appContext, settings.reminderMinutes, replace = false)
        } catch (e: Exception) {
            Log.w("Spendora", "Could not schedule reminder; trying the popup", e)
        }

        // Instant popup over whatever app is open. Holding "Display over other apps"
        // (SYSTEM_ALERT_WINDOW) exempts the app from background-activity-start limits.
        // Without it the system quietly blocks this start while we are in the background,
        // and the full-screen-intent notification above takes over.
        if (!Settings.canDrawOverlays(appContext)) {
            Log.i("Spendora", "Overlay permission missing; popup may be limited to the notification")
        }
        withContext(Dispatchers.Main) {
            try {
                appContext.startActivity(NotificationHelper.popupIntent(appContext, id))
            } catch (e: Exception) {
                Log.w("Spendora", "Could not open popup; notification remains", e)
            }
        }
        return Outcome.DETECTED
    }
}
