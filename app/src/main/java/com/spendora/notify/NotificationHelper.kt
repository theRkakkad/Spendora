package com.spendora.notify

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.spendora.R
import com.spendora.spendoraApp
import com.spendora.data.Expense
import com.spendora.ui.MainActivity
import com.spendora.ui.popup.PopupActivity
import com.spendora.util.Format

/**
 * Posts the persistent "add details" notifications for PENDING expenses.
 * A notification is only removed when its expense is saved or deleted.
 */
object NotificationHelper {

    const val CHANNEL_ID = "spendora_pending_expenses"
    const val ACTION_DISMISSED = "com.spendora.action.NOTIFICATION_DISMISSED"
    const val EXTRA_EXPENSE_ID = "expense_id"

    private const val GROUP_KEY = "com.spendora.PENDING_GROUP"
    private const val SUMMARY_ID = 1
    private const val SUMMARY_REQUEST_CODE = 2
    private const val SUMMARY_DISMISS_REQUEST_CODE = 3
    private const val ID_OFFSET = 1000

    fun notificationId(expenseId: Long): Int = ID_OFFSET + (expenseId % 1_000_000L).toInt()

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_pending_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = context.getString(R.string.channel_pending_description)
        channel.enableVibration(true)
        channel.setShowBadge(true)
        channel.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        manager.createNotificationChannel(channel)
    }

    fun popupIntent(context: Context, expenseId: Long): Intent =
        Intent(context, PopupActivity::class.java)
            .setData(Uri.parse("spendora://expense/$expenseId"))
            .putExtra(EXTRA_EXPENSE_ID, expenseId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private fun popupPendingIntent(context: Context, expenseId: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            notificationId(expenseId),
            popupIntent(context, expenseId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun dismissedPendingIntent(context: Context, expenseId: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(ACTION_DISMISSED)
            .setData(Uri.parse("spendora://dismissed/$expenseId"))
            .putExtra(EXTRA_EXPENSE_ID, expenseId)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

    /**
     * Posts (or re-posts) the ongoing notification for one pending expense.
     *
     * @param alert play sound / heads-up; false re-posts quietly.
     * @param fullScreen attach a full-screen intent so the popup opens over the lock screen
     *                   or shows as a heads-up when the phone is in use.
     */
    @SuppressLint("MissingPermission")
    fun postPending(context: Context, expense: Expense, alert: Boolean, fullScreen: Boolean) {
        createChannel(context)
        if (!canPost(context)) return
        val open = popupPendingIntent(context, expense.id)
        val title = Format.money(expense.amount) + " spent - tap to add details"
        val text = buildString {
            append(Format.dateTime(expense.timestamp))
            if (expense.sender.isNotBlank()) append(" • ").append(expense.sender)
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rupee)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setWhen(expense.timestamp)
            .setShowWhen(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setDeleteIntent(
                dismissedPendingIntent(context, expense.id, notificationId(expense.id))
            )
            .addAction(R.drawable.ic_stat_rupee, "Add details", open)
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        if (alert) {
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        } else {
            builder.setSilent(true)
        }
        // Android 14+ may deny full-screen access. Keep the ordinary actionable
        // notification even when a lock-screen launch is unavailable.
        if (fullScreen && canUseFullScreen(context)) {
            builder.setFullScreenIntent(open, true)
        }
        try {
            NotificationManagerCompat.from(context).notify(notificationId(expense.id), builder.build())
        } catch (e: SecurityException) {
            // Notification permission was revoked between the check and the call.
        }
    }

    /** Re-posts every pending notification (after reboot, swipe-away, or a periodic reminder). */
    suspend fun refreshAll(context: Context, alert: Boolean) {
        val pending = context.spendoraApp.repository.getPending()
        pending.forEachIndexed { index, expense ->
            postPending(context, expense, alert = alert && index == 0, fullScreen = false)
        }
        postSummary(context, pending)
    }

    /** Shows the group summary when two or more expenses are pending, otherwise removes it. */
    suspend fun updateSummary(context: Context) {
        postSummary(context, context.spendoraApp.repository.getPending())
    }

    @SuppressLint("MissingPermission")
    private fun postSummary(context: Context, pending: List<Expense>) {
        val manager = NotificationManagerCompat.from(context)
        if (pending.size < 2) {
            manager.cancel(SUMMARY_ID)
            return
        }
        if (!canPost(context)) return
        val total = pending.sumOf { it.amount }
        val title = "${pending.size} payments need details"
        val text = Format.money(total) + " spent - tap to add details"
        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText(Format.money(total) + " pending")
        pending.take(6).forEach { expense ->
            style.addLine(Format.money(expense.amount) + "  •  " + Format.dateTime(expense.timestamp))
        }
        val openApp = PendingIntent.getActivity(
            context,
            SUMMARY_REQUEST_CODE,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rupee)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp)
            .setDeleteIntent(dismissedPendingIntent(context, -1L, SUMMARY_DISMISS_REQUEST_CODE))
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        try {
            manager.notify(SUMMARY_ID, builder.build())
        } catch (e: SecurityException) {
            // Notification permission was revoked between the check and the call.
        }
    }

    fun cancel(context: Context, expenseId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(expenseId))
    }
}
