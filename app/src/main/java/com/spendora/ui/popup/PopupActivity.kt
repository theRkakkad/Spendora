package com.spendora.ui.popup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendora.data.Expense
import com.spendora.notify.NotificationHelper
import com.spendora.ui.theme.SpendoraTheme
import com.spendora.util.Format

/**
 * Dialog-styled, translucent activity shown on top of whatever app is open as soon as a
 * debit SMS is detected. "Later" just closes it; the ongoing notification stays.
 */
class PopupActivity : ComponentActivity() {

    private val viewModel: PopupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        viewModel.load(expenseIdOf(intent))

        setContent {
            SpendoraTheme {
                val expense by viewModel.expense.collectAsState()
                val finished by viewModel.finished.collectAsState()
                val labels by viewModel.recentLabels.collectAsState()

                LaunchedEffect(finished) {
                    if (finished) finish()
                }

                val current = expense
                if (current != null) {
                    PopupContent(
                        expense = current,
                        labels = labels,
                        onSave = { amount, label -> viewModel.save(amount, label) },
                        onLater = { finish() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.load(expenseIdOf(intent))
    }

    private fun expenseIdOf(intent: Intent?): Long =
        intent?.getLongExtra(NotificationHelper.EXTRA_EXPENSE_ID, -1L) ?: -1L
}

@Composable
private fun PopupContent(
    expense: Expense,
    labels: List<String>,
    onSave: (Double, String) -> Unit,
    onLater: () -> Unit
) {
    var amountText by remember(expense.id) { mutableStateOf(Format.plainAmount(expense.amount)) }
    var label by remember(expense.id) { mutableStateOf(expense.label) }
    var showErrors by remember(expense.id) { mutableStateOf(false) }

    val amount = Format.parseAmount(amountText)
    val amountValid = amount != null
    val labelValid = label.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = Format.money(expense.amount) + " spent",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(Format.dateTime(expense.timestamp))
                        if (expense.sender.isNotBlank()) append(" • ").append(expense.sender)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    isError = showErrors && !amountValid,
                    supportingText = {
                        if (showErrors && !amountValid) Text("Enter a valid amount")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Paid to / Type of payment") },
                    placeholder = { Text("e.g. Vraj - recharge") },
                    singleLine = true,
                    isError = showErrors && !labelValid,
                    supportingText = {
                        if (showErrors && !labelValid) Text("Say who or what this was for")
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (labels.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        labels.forEach { suggestion ->
                            SuggestionChip(
                                onClick = { label = suggestion },
                                label = { Text(suggestion) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onLater) { Text("Later") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (amount != null && labelValid) {
                                onSave(amount, label)
                            } else {
                                showErrors = true
                            }
                        }
                    ) { Text("Save") }
                }
            }
        }
    }
}
