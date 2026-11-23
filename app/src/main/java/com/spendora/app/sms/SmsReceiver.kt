package com.spendora.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manifest-registered receiver for incoming SMS. Parsing happens entirely on the device. */
class SmsReceiver : BroadcastReceiver() {

    private class Incoming(val timestamp: Long) {
        val body = StringBuilder()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            null
        }
        if (messages == null || messages.isEmpty()) return

        // Long SMS arrive as several parts from the same sender: join them back together.
        val bySender = LinkedHashMap<String, Incoming>()
        for (message in messages) {
            if (message == null) continue
            val sender = message.displayOriginatingAddress ?: message.originatingAddress ?: ""
            val timestamp = if (message.timestampMillis > 0) message.timestampMillis else System.currentTimeMillis()
            val incoming = bySender.getOrPut(sender) { Incoming(timestamp) }
            incoming.body.append(message.displayMessageBody ?: message.messageBody ?: "")
        }
        if (bySender.isEmpty()) return

        val appContext = context.applicationContext
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for ((sender, incoming) in bySender) {
                    SmsProcessor.process(appContext, sender, incoming.body.toString(), incoming.timestamp)
                }
            } catch (e: Exception) {
                Log.w("Spendora", "SMS processing failed", e)
            } finally {
                result.finish()
            }
        }
    }
}
