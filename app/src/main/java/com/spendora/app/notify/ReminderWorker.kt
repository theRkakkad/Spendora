package com.spendora.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Periodically re-alerts for expenses that are still waiting for details. */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            NotificationHelper.refreshAll(applicationContext, alert = true)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

object ReminderScheduler {

    private const val WORK_NAME = "spendora_pending_reminder"

    /**
     * @param minutes reminder interval (WorkManager's minimum is 15 minutes).
     * @param replace true when the interval setting changed, false to keep an existing schedule.
     */
    fun schedule(context: Context, minutes: Int, replace: Boolean) {
        val interval = minutes.coerceAtLeast(15).toLong()
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(interval, TimeUnit.MINUTES)
            .setInitialDelay(interval, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
