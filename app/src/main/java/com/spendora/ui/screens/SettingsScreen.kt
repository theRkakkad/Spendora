package com.spendora.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.spendora.data.SettingsStore
import com.spendora.sms.SmsProcessor
import com.spendora.ui.MainViewModel
import com.spendora.util.SetupChecks

private val StatusGreen = Color(0xFF2E7D32)
private val StatusRed = Color(0xFFC62828)

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    userName: String,
    resumeTick: Int,
    onBack: () -> Unit,
    onOpenSmsTester: () -> Unit
) {
    val context = LocalContext.current
    val reminderMinutes by viewModel.reminderMinutes.collectAsState()
    val savedSenders by viewModel.sendersText.collectAsState()
    val savedKeywords by viewModel.keywordsText.collectAsState()

    // Re-read statuses when returning from a system settings screen or a permission dialog.
    var localTick by remember { mutableIntStateOf(0) }
    val status = remember(resumeTick, localTick) { SetupChecks.read(context) }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        localTick++
        if (result.values.any { !it }) {
            // Denied (or blocked by "restricted settings"): send the user to the app's settings.
            Toast.makeText(
                context,
                "Allow SMS under Permissions. If it is greyed out, use ⋮ > Allow restricted settings first.",
                Toast.LENGTH_LONG
            ).show()
            SetupChecks.openAppDetails(context)
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        localTick++
        if (!granted) SetupChecks.openNotificationSettings(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & setup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Name ----------------------------------------------------------------------
            SectionCard(title = "Your name") {
                var nameText by remember(userName) { mutableStateOf(userName) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        singleLine = true,
                        label = { Text("Name") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.setUserName(nameText)
                            Toast.makeText(context, "Name saved", Toast.LENGTH_SHORT).show()
                        },
                        enabled = nameText.isNotBlank() && nameText.trim() != userName
                    ) { Text("Save") }
                }
            }

            // ---- Setup checklist -----------------------------------------------------------
            SectionCard(title = "Setup checklist") {
                CheckRow(
                    title = "SMS permission",
                    detail = "Detects new bank debit / UPI SMS on this phone. Does not read your SMS inbox.",
                    ok = status.sms,
                    actionLabel = "Allow",
                    onFix = {
                        smsLauncher.launch(
                            arrayOf(Manifest.permission.RECEIVE_SMS)
                        )
                    }
                )
                CheckRow(
                    title = "Notifications",
                    detail = "Needed for the \"tap to add details\" reminder.",
                    ok = status.notifications,
                    actionLabel = "Allow",
                    onFix = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            SetupChecks.openPaymentNotificationSettings(context)
                        }
                    }
                )
                CheckRow(
                    title = "Appear on top (overlay)",
                    detail = "Lets the popup open instantly over Google Pay, PhonePe or any other app.",
                    ok = status.overlay,
                    actionLabel = "Allow",
                    onFix = { SetupChecks.openOverlaySettings(context) }
                )
                CheckRow(
                    title = "Full-screen notifications",
                    detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        "Optional lock-screen prompt. Payment notifications still work without this access."
                    } else {
                        "Granted automatically on this Android version."
                    },
                    ok = status.fullScreenIntent,
                    actionLabel = "Allow",
                    onFix = { SetupChecks.openFullScreenIntentSettings(context) }
                )
                CheckRow(
                    title = "Battery: unrestricted",
                    detail = "Stops the phone from putting the app to sleep and missing messages.",
                    ok = status.batteryUnrestricted,
                    actionLabel = "Fix",
                    onFix = { SetupChecks.requestIgnoreBatteryOptimizations(context) }
                )
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = { SetupChecks.openAppDetails(context) }) {
                    Text("Open app info")
                }
                TextButton(onClick = { SetupChecks.openPaymentNotificationSettings(context) }) {
                    Text("Payment notification sound & pop-up")
                }
            }

            // ---- Samsung guidance ----------------------------------------------------------
            SectionCard(title = "Samsung (One UI) tips") {
                GuidanceLine(
                    "Allow restricted settings",
                    "If SMS or \"Appear on top\" is greyed out: Open app info, tap ⋮ (top right), " +
                        "choose \"Allow restricted settings\", confirm with your PIN, then come back and tap Allow."
                )
                GuidanceLine(
                    "Battery: Unrestricted",
                    "Open app info > Battery > choose \"Unrestricted\"."
                )
                GuidanceLine(
                    "Remove from Sleeping apps",
                    "Settings > Battery (or Device care > Battery) > Background usage limits. Make sure " +
                        "this app is not in \"Sleeping apps\" or \"Deep sleeping apps\", and add it to " +
                        "\"Never sleeping apps\"."
                )
                GuidanceLine(
                    "Keep notifications loud",
                    "Open app info > Notifications > \"Pending expenses\" > allow sound and pop-up."
                )
            }

            // ---- Test ----------------------------------------------------------------------
            SectionCard(title = "Test") {
                Text(
                    text = "Creates a sample ₹499 payment to check parsing, the pending entry, " +
                        "notification and popup. This does not check receipt of a real SMS. " +
                        "Delete the sample afterwards if you do not want to keep it.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.simulateTestSms { outcome ->
                            val message = when (outcome) {
                                SmsProcessor.Outcome.DETECTED ->
                                    "Test payment detected - check the popup and notification"
                                SmsProcessor.Outcome.DUPLICATE ->
                                    "Test SMS was treated as a duplicate"
                                SmsProcessor.Outcome.IGNORED ->
                                    "Test SMS was ignored - check that HDFC is in the sender list"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }
                ) { Text("Test popup") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenSmsTester) {
                    Text("Test an SMS rule")
                }
            }

            Text(
                text = "Automatic detection starts when your bank sends a supported debit SMS. " +
                    "A payment without a bank SMS cannot trigger the popup.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // ---- Reminder interval ---------------------------------------------------------
            SectionCard(title = "Remind me about pending payments every") {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SettingsStore.REMINDER_OPTIONS.forEach { minutes ->
                        FilterChip(
                            selected = reminderMinutes == minutes,
                            onClick = { viewModel.setReminderMinutes(minutes) },
                            label = { Text(intervalLabel(minutes)) }
                        )
                    }
                }
            }

            // ---- Sender list ---------------------------------------------------------------
            SectionCard(title = "Bank senders") {
                var sendersText by remember(savedSenders) { mutableStateOf(savedSenders) }
                Text(
                    text = "One per line. An SMS is checked only if its sender name contains one of " +
                        "these (for example VM-HDFCBK contains HDFC). Leave empty to check every sender.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sendersText,
                    onValueChange = { sendersText = it },
                    minLines = 4,
                    maxLines = 8,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.saveSendersText(sendersText)
                            Toast.makeText(context, "Sender list saved", Toast.LENGTH_SHORT).show()
                        },
                        enabled = sendersText != savedSenders
                    ) { Text("Save") }
                    TextButton(
                        onClick = {
                            viewModel.resetSenders()
                            sendersText = SettingsStore.DEFAULT_SENDERS_TEXT
                        }
                    ) { Text("Reset to defaults") }
                }
            }

            // ---- Extra keywords ------------------------------------------------------------
            SectionCard(title = "Extra debit keywords") {
                var keywordsText by remember(savedKeywords) { mutableStateOf(savedKeywords) }
                Text(
                    text = "One per line. Built in: debited, spent, paid, sent, withdrawn, transferred, " +
                        "deducted, purchase, Dr. Add words your bank uses that are missed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = keywordsText,
                    onValueChange = { keywordsText = it },
                    minLines = 2,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.saveKeywordsText(keywordsText)
                        Toast.makeText(context, "Keywords saved", Toast.LENGTH_SHORT).show()
                    },
                    enabled = keywordsText != savedKeywords
                ) { Text("Save") }
            }

            Text(
                text = "Everything stays on this phone. The app has no internet permission; " +
                    "data only leaves when you export a CSV yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
    }
}

private fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes == 60 -> "1 hour"
    else -> "${minutes / 60} hours"
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun CheckRow(
    title: String,
    detail: String,
    ok: Boolean,
    actionLabel: String,
    onFix: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(if (ok) StatusGreen else StatusRed, CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        if (ok) {
            Text(
                text = "On",
                style = MaterialTheme.typography.labelLarge,
                color = StatusGreen,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        } else {
            TextButton(onClick = onFix) { Text(actionLabel) }
        }
    }
}

@Composable
private fun GuidanceLine(title: String, detail: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
