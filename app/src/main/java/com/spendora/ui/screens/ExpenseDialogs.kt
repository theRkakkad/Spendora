package com.spendora.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendora.data.Account
import com.spendora.data.ExpenseCategory
import com.spendora.util.Format
import java.util.Calendar

/**
 * Shared editor for "add manual expense", "edit expense" and "add details" on a pending one.
 */
@Composable
fun ExpenseEditorDialog(
    title: String,
    initialAmount: Double?,
    initialLabel: String,
    initialTimestamp: Long,
    suggestions: List<String>,
    initialCategory: String = ExpenseCategory.OTHER.id,
    initialAccountId: Long = 1L,
    initialReceiptUri: String? = null,
    accounts: List<Account> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        amount: Double,
        label: String,
        timestamp: Long,
        category: String,
        accountId: Long,
        receiptUri: String?
    ) -> Unit
) {
    val context = LocalContext.current
    var amountText by remember {
        mutableStateOf(if (initialAmount != null) Format.plainAmount(initialAmount) else "")
    }
    var label by remember { mutableStateOf(initialLabel) }
    var timestamp by remember { mutableLongStateOf(initialTimestamp) }
    var category by remember { mutableStateOf(initialCategory) }
    var accountId by remember { mutableLongStateOf(initialAccountId) }
    var receiptUri by remember { mutableStateOf(initialReceiptUri) }
    var showErrors by remember { mutableStateOf(false) }
    var accountMenuOpen by remember { mutableStateOf(false) }

    val receiptLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            receiptUri = uri.toString()
        }
    }

    val amount = Format.parseAmount(amountText)
    val labelValid = label.isNotBlank()
    val selectedAccount = accounts.firstOrNull { it.id == accountId }
        ?: accounts.firstOrNull()
        ?: Account(id = 1L, name = "Cash", type = "CASH", color = 0xFF4CAF50)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    isError = showErrors && amount == null,
                    supportingText = {
                        if (showErrors && amount == null) Text("Enter a valid amount")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
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
                if (suggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            SuggestionChip(
                                onClick = { label = suggestion },
                                label = { Text(suggestion) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CategoryPicker(selected = category, onSelect = { category = it })

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box {
                    OutlinedButton(onClick = { accountMenuOpen = true }) {
                        Text(selectedAccount.name)
                    }
                    DropdownMenu(
                        expanded = accountMenuOpen,
                        onDismissRequest = { accountMenuOpen = false }
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    accountId = account.id
                                    accountMenuOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Receipt",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { receiptLauncher.launch("image/*") }) {
                        Text(if (receiptUri == null) "Attach receipt" else "Replace receipt")
                    }
                    if (receiptUri != null) {
                        OutlinedButton(onClick = {
                            runCatching { openReceipt(context, receiptUri!!) }
                                .onFailure {
                                    Toast.makeText(context, "Cannot open receipt", Toast.LENGTH_SHORT).show()
                                }
                        }) { Text("View") }
                        TextButton(onClick = { receiptUri = null }) { Text("Remove") }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Date and time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
                            DatePickerDialog(
                                context,
                                { _, year, month, day ->
                                    val updated = Calendar.getInstance().apply {
                                        timeInMillis = timestamp
                                        set(Calendar.YEAR, year)
                                        set(Calendar.MONTH, month)
                                        set(Calendar.DAY_OF_MONTH, day)
                                    }
                                    timestamp = updated.timeInMillis
                                },
                                calendar.get(Calendar.YEAR),
                                calendar.get(Calendar.MONTH),
                                calendar.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                    ) { Text(Format.date(timestamp)) }

                    OutlinedButton(
                        onClick = {
                            val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    val updated = Calendar.getInstance().apply {
                                        timeInMillis = timestamp
                                        set(Calendar.HOUR_OF_DAY, hour)
                                        set(Calendar.MINUTE, minute)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    timestamp = updated.timeInMillis
                                },
                                calendar.get(Calendar.HOUR_OF_DAY),
                                calendar.get(Calendar.MINUTE),
                                false
                            ).show()
                        }
                    ) { Text(Format.time(timestamp)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (amount != null && labelValid) {
                        onSave(amount, label.trim(), timestamp, category, accountId, receiptUri)
                    } else {
                        showErrors = true
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun openReceipt(context: Context, uriString: String) {
    val uri = Uri.parse(uriString)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}

@Composable
fun ConfirmDeleteDialog(
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete expense?") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
