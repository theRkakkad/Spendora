package com.spendora.util

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.spendora.notify.NotificationHelper

data class SetupStatus(
    val sms: Boolean,
    val notifications: Boolean,
    val overlay: Boolean,
    val fullScreenIntent: Boolean,
    val batteryUnrestricted: Boolean
) {
    val allGood: Boolean
        get() = sms && notifications && overlay
}

object SetupChecks {

    fun read(context: Context): SetupStatus {
        val sms = granted(context, Manifest.permission.RECEIVE_SMS)

        val notifications = NotificationHelper.canPost(context)

        val overlay = Settings.canDrawOverlays(context)

        val fullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false
        } else {
            true
        }

        val battery = context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        return SetupStatus(sms, notifications, overlay, fullScreen, battery)
    }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun packageUri(context: Context): Uri = Uri.parse("package:" + context.packageName)

    fun openAppDetails(context: Context) {
        launch(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)), null)
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        launch(context, intent, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))
    }

    fun openPaymentNotificationSettings(context: Context) {
        launch(
            context,
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.CHANNEL_ID),
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }

    fun openOverlaySettings(context: Context) {
        launch(
            context,
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context)),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))
        )
    }

    fun openFullScreenIntentSettings(context: Context) {
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            launch(
                context,
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context)),
                fallback
            )
        } else {
            launch(context, fallback, null)
        }
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        launch(
            context,
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))
        )
    }

    private fun launch(context: Context, intent: Intent, fallback: Intent?) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            if (fallback != null) {
                try {
                    context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (ignored: Exception) {
                    // Nothing else we can open on this device.
                }
            }
        } catch (e: SecurityException) {
            // Some OEM builds guard these screens; the user can still reach them manually.
        }
    }
}
