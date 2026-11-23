package com.expensetracker.app.ui.screens

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expensetracker.app.data.Expense
import com.expensetracker.app.ui.MainViewModel
import com.expensetracker.app.util.Format
import com.expensetracker.app.util.SetupChecks
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    userName: String,
    resumeTick: Int,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.dashboard.collectAsState()
    val search by viewModel.search.collectAsState()
    val labels by viewModel.recentLabels.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var deleting by remember { mutableStateOf<Expense?>(null) }
    var showExport by remember { mutableStateOf(false) }
    // Which range the file picker was opened for: 0 = none, 1 = this month, 2 = all time.
    var saveRange by rememberSaveable { mutableStateOf(0) }

    val setup = remember(resumeTick) { SetupChecks.read(context) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val range = saveRange
        saveRange = 0
        if (uri != null && range != 0) {
            scope.launch {
                val ok = viewModel.writeCsv(uri, allTime = range == 2)
                Toast.makeText(
                    context,
                    if (ok) "CSV saved" else "Could not save the CSV",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Hi, $userName",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings and setup")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add expense") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!setup.allGood) {
                item(key = "setup-banner") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (!setup.sms) {
                                    "SMS access is off, so payments can't be detected yet."
                                } else {
                                    "Setup is incomplete - popups or reminders may not appear."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onOpenSettings) { Text("Fix") }
                        }
                    }
                }
            }

            item(key = "totals") {
                TotalsCard(
                    monthLabel = Format.month(state.month),
                    monthTotal = state.monthTotal,
                    todayTotal = state.todayTotal,
                    pendingTotal = state.pendingTotal,
                    onPrevious = { viewModel.previousMonth() },
                    onNext = { viewModel.nextMonth() },
                    onExport = { showExport = true }
                )
            }

            if (state.pending.isNotEmpty()) {
                item(key = "pending-header") {
                    Text(
                        text = "Pending (${state.pending.size})",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(state.pending, key = { "pending-${it.id}" }) { expense ->
                    PendingCard(
                        expense = expense,
                        onAddDetails = { editing = expense },
                        onDelete = { deleting = expense }
                    )
                }
            }

            item(key = "search") {
                OutlinedTextField(
                    value = search,
                    onValueChange = { viewModel.setSearch(it) },
                    singleLine = true,
                    placeholder = { Text("Search by paid to / type") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearch("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }

            if (state.saved.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = when {
                            state.hasAnySavedInMonth -> "No expenses match \"${search.trim()}\"."
                            else -> "No expenses saved in ${Format.month(state.month)}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(state.saved, key = { "saved-${it.id}" }) { expense ->
                    SavedRow(
                        expense = expense,
                        onEdit = { editing = expense },
                        onDelete = { deleting = expense }
                    )
                }
            }
        }
    }

    if (showAdd) {
        ExpenseEditorDialog(
            title = "Add expense",
            initialAmount = null,
            initialLabel = "",
            initialTimestamp = remember { System.currentTimeMillis() },
            suggestions = labels,
            onDismiss = { showAdd = false },
            onSave = { amount, label, timestamp ->
                viewModel.addManual(amount, label, timestamp)
                showAdd = false
            }
        )
    }

    val toEdit = editing
    if (toEdit != null) {
        ExpenseEditorDialog(
            title = if (toEdit.status == Expense.STATUS_PENDING) "Add details" else "Edit expense",
            initialAmount = toEdit.amount,
            initialLabel = toEdit.label,
            initialTimestamp = toEdit.timestamp,
            suggestions = labels,
            onDismiss = { editing = null },
            onSave = { amount, label, timestamp ->
                viewModel.saveExpense(toEdit, amount, label, timestamp)
                editing = null
            }
        )
    }

    val toDelete = deleting
    if (toDelete != null) {
        val what = if (toDelete.label.isNotBlank()) "\"${toDelete.label}\"" else "this pending payment"
        ConfirmDeleteDialog(
            message = "Delete ${Format.money(toDelete.amount)} for $what? This can't be undone.",
            onDismiss = { deleting = null },
            onConfirm = {
                viewModel.delete(toDelete)
                deleting = null
            }
        )
    }

    if (showExport) {
        ExportDialog(
            monthLabel = Format.month(state.month),
            onDismiss = { showExport = false },
            onSaveToFile = { allTime ->
                showExport = false
                scope.launch {
                    val file = viewModel.buildCsv(allTime)
                    saveRange = if (allTime) 2 else 1
                    try {
                        saveLauncher.launch(file.fileName)
                    } catch (e: Exception) {
                        saveRange = 0
                        Toast.makeText(context, "No file picker available", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onShare = { allTime ->
                showExport = false
                scope.launch {
                    val uri = viewModel.csvShareUri(allTime)
                    if (uri == null) {
                        Toast.makeText(context, "Could not create the CSV", Toast.LENGTH_SHORT).show()
                    } else {
                        shareCsv(context, uri)
                    }
                }
            }
        )
    }
}

private fun shareCsv(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Expenses")
        clipData = ClipData.newRawUri("Expenses CSV", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, "Share CSV"))
    } catch (e: Exception) {
        Toast.makeText(context, "No app available to share with", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun TotalsCard(
    monthLabel: String,
    monthTotal: Double,
    todayTotal: Double,
    pendingTotal: Double,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    text = monthLabel,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(
                    text = Format.money(monthTotal),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "spent this month",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Today: " + Format.money(todayTotal),
                    style = MaterialTheme.typography.titleMedium
                )
                if (pendingTotal > 0.0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = Format.money(pendingTotal) + " more is pending details (not in totals)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = onExport) { Text("Export CSV") }
            }
        }
    }
}

@Composable
private fun PendingCard(
    expense: Expense,
    onAddDetails: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Format.money(expense.amount),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = buildString {
                        append(Format.dateTime(expense.timestamp))
                        if (expense.sender.isNotBlank()) append(" • ").append(expense.sender)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onAddDetails) { Text("Add details") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete pending payment")
            }
        }
    }
}

@Composable
private fun SavedRow(
    expense: Expense,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.label.ifBlank { "(no label)" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = Format.dateTime(expense.timestamp) + " • " +
                        (if (expense.isManual) "Manual" else "SMS"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = Format.money(expense.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit expense")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete expense")
            }
        }
    }
}

@Composable
private fun ExportDialog(
    monthLabel: String,
    onDismiss: () -> Unit,
    onSaveToFile: (allTime: Boolean) -> Unit,
    onShare: (allTime: Boolean) -> Unit
) {
    var allTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export CSV") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = !allTime, onClick = { allTime = false }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = !allTime, onClick = { allTime = false })
                    Text("This month ($monthLabel)")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = allTime, onClick = { allTime = true }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = allTime, onClick = { allTime = true })
                    Text("All time")
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSaveToFile(allTime) }) { Text("Save to file") }
                    OutlinedButton(onClick = { onShare(allTime) }) { Text("Share") }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
