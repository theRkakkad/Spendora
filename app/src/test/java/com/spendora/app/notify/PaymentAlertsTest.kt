package com.spendora.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.provider.Telephony
import com.spendora.app.SpendoraApp
import com.spendora.app.data.Expense
import com.spendora.app.sms.SmsReceiver
import com.spendora.app.ui.popup.PopupActivity
import com.spendora.app.util.SetupChecks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNotificationManager

/** Regression coverage for the components and permissions removed by the manual-only rebuild. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = SpendoraApp::class, shadows = [FullScreenDeniedManager::class])
class PaymentAlertsTest {
    private lateinit var context: SpendoraApp
    private lateinit var manager: NotificationManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication() as SpendoraApp
        manager = context.getSystemService(NotificationManager::class.java)
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(true)
        NotificationHelper.createChannel(context)
    }

    private fun payment(id: Long) = Expense(
        id = id,
        amount = 499.0,
        timestamp = 1_795_000_000_000L,
        sender = "VM-HDFCBK",
        dedupeKey = "test:$id"
    )

    @Test
    fun automatic_payment_components_are_registered_and_protected() {
        val receiver = context.packageManager.getReceiverInfo(ComponentName(context, SmsReceiver::class.java), 0)
        assertTrue(receiver.exported)
        assertEquals(Manifest.permission.BROADCAST_SMS, receiver.permission)
        val listeners = context.packageManager.queryBroadcastReceivers(
            Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).setPackage(context.packageName), 0
        )
        assertTrue(listeners.any { it.activityInfo.name == SmsReceiver::class.java.name })
        val popup = context.packageManager.getActivityInfo(ComponentName(context, PopupActivity::class.java), 0)
        assertFalse(popup.exported)
        val permissions = context.packageManager.getPackageInfo(
            context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS
        ).requestedPermissions.toList()
        assertTrue(permissions.contains(Manifest.permission.RECEIVE_SMS))
        assertFalse(permissions.contains(Manifest.permission.READ_SMS))
    }

    @Test
    fun sms_setup_requires_only_new_incoming_sms_access() {
        shadowOf(context).grantPermissions(Manifest.permission.RECEIVE_SMS)
        shadowOf(context).denyPermissions(Manifest.permission.READ_SMS)
        assertTrue(SetupChecks.read(context).sms)
    }

    @Test
    fun denied_fullscreen_access_keeps_the_actionable_payment_notification() {
        NotificationHelper.postPending(context, payment(41L), alert = true, fullScreen = true)
        val notification = shadowOf(manager).getNotification(NotificationHelper.notificationId(41L))
        assertNotNull(notification)
        assertNull(notification.fullScreenIntent)
        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(PopupActivity::class.java.name, intent.component!!.className)
        assertEquals(41L, intent.getLongExtra(NotificationHelper.EXTRA_EXPENSE_ID, -1L))
        assertEquals("Add details", notification.actions.first().title)
    }

    @Test
    fun denied_notifications_are_reported_without_posting() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(SetupChecks.read(context).notifications)
        NotificationHelper.postPending(context, payment(42L), alert = true, fullScreen = true)
        assertNull(shadowOf(manager).getNotification(NotificationHelper.notificationId(42L)))
    }

    @Test
    fun blocked_payment_channel_is_detected_even_when_app_notifications_are_enabled() {
        manager.createNotificationChannel(
            NotificationChannel(NotificationHelper.CHANNEL_ID, "Pending expenses", NotificationManager.IMPORTANCE_NONE)
        )
        assertFalse(SetupChecks.read(context).notifications)
    }

    @Test
    fun different_payments_open_their_own_popup() {
        NotificationHelper.postPending(context, payment(43L), alert = true, fullScreen = false)
        NotificationHelper.postPending(context, payment(44L), alert = true, fullScreen = false)
        for (id in listOf(43L, 44L)) {
            val notification = shadowOf(manager).getNotification(NotificationHelper.notificationId(id))
            assertEquals(id, shadowOf(notification.contentIntent).savedIntent.getLongExtra(NotificationHelper.EXTRA_EXPENSE_ID, -1L))
        }
    }

    @Test
    fun payment_notification_settings_open_the_actual_channel() {
        SetupChecks.openPaymentNotificationSettings(context)
        val intent = shadowOf(context).nextStartedActivity
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(NotificationHelper.CHANNEL_ID, intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }
}

/** Model a phone on which Android has refused optional lock-screen full-screen access. */
@Implements(NotificationManager::class)
class FullScreenDeniedManager : ShadowNotificationManager() {
    @Implementation(minSdk = 34)
    fun canUseFullScreenIntent(): Boolean = false
}
