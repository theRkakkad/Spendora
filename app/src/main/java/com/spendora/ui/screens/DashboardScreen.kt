package com.spendora.ui.screens

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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import com.spendora.data.Expense
import com.spendora.export.CsvExporter
import com.spendora.ui.AppDestination
import com.spendora.ui.MainViewModel
import com.spendora.util.Format
import com.spendora.util.SetupChecks
import kotlinx.coroutines.launch

private data class ExportSettings(
    val options: CsvExporter.CsvOptions = CsvExporter.CsvOptions(),
    val allTime: Boolean = false,
    val startDate: String? = null,
    val endDate: String? = null
)

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    userName: String,
    resumeTick: Int,
    openAddInitially: Boolean = false,
    onNavigate: (AppDestination) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.dashboard.collectAsState()
    val search by viewModel.search.collectAsState()
    val labels by viewModel.recentLabels.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val selection by viewModel.selection.collectAsState()

    var showAdd by rememberSaveable { mutableStateOf(openAddInitially) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var deleting by remember { mutableStateOf<Expense?>(null) }
    var showExport by remember { mutableStateOf(false) }
    // Which range the file picker was opened for: 0 = none, 1 = custom export.
    var saveExport by rememberSaveable { mutableStateOf(false) }
    var exportSettings by remember { mutableStateOf(ExportSettings()) }

    val setup = remember(resumeTick) { SetupChecks.read(context) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val shouldWrite = saveExport
        saveExport = false
        if (uri != null && shouldWrite) {
            scope.launch {
                val ok = viewModel.writeCsv(
                    uri = uri,
                    options = exportSettings.options,
                    allTime = exportSettings.allTime,
                    startDate = exportSettings.startDate,
                    endDate = exportSettings.endDate
                )
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
                    IconButton(onClick = { onNavigate(AppDestination.INSIGHTS) }) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Spending insights")
                    }
                    IconButton(onClick = { onNavigate(AppDestination.BUDGET) }) {
                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Monthly budget")
                    }
                    IconButton(onClick = { onNavigate(AppDestination.SPLITS) }) {
                        Icon(Icons.Filled.Group, contentDescription = "Split expenses")
                    }
                    IconButton(onClick = { onNavigate(AppDestination.BACKUP) }) {
                        Icon(Icons.Filled.Backup, contentDescription = "Backup and restore")
                    }
                    IconButton(onClick = { onNavigate(AppDestination.SETTINGS) }) {
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
                            TextButton(onClick = { onNavigate(AppDestination.SETTINGS) }) { Text("Fix") }
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

            item(key = "bulk") {
                BulkBar(
                    selectedCount = selection.size,
                    accounts = state.accounts,
                    onClear = { viewModel.clearSelection() },
                    onDelete = { viewModel.bulkDelete() },
                    onSetCategory = { viewModel.bulkSetCategory(it) },
                    onSetAccount = { viewModel.bulkSetAccount(it) }
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
                        selected = expense.id in selection,
                        onToggleSelection = { viewModel.toggleSelection(expense.id) },
                        onAddDetails = { editing = expense },
                        onDelete = { deleting = expense }
                    )
                }
            }

            item(key = "filters") {
                FilterBar(
                    category = filters.category,
                    source = filters.source,
                    status = filters.status,
                    minAmount = filters.minAmount,
                    maxAmount = filters.maxAmount,
                    onCategory = { viewModel.setCategoryFilter(it) },
                    onSource = { viewModel.setSourceFilter(it) },
                    onStatus = { viewModel.setStatusFilter(it) },
                    onMinAmount = { viewModel.setMinAmountFilter(it) },
                    onMaxAmount = { viewModel.setMaxAmountFilter(it) },
                    onClear = { viewModel.clearFilters() }
                )
            }

            item(key = "search") {
                OutlinedTextField(
                    value = search,
                    onValueChange = { viewModel.setSearch(it) },
                    singleLine = true,
                    placeholder = { Text("Search by paid to / type / category") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearch("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.saved.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = when {
                            state.hasAnySavedInMonth -> "No expenses match the current filters."
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
                        selected = expense.id in selection,
                        onToggleSelection = { viewModel.toggleSelection(expense.id) },
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
            accounts = state.accounts,
            onDismiss = { showAdd = false },
            onSave = { amount, label, timestamp, category, accountId, receiptUri ->
                viewModel.addManual(amount, label, timestamp, category, accountId, receiptUri)
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
            initialCategory = toEdit.category,
            initialAccountId = toEdit.accountId,
            initialReceiptUri = toEdit.receiptUri,
            suggestions = labels,
            accounts = state.accounts,
            onDismiss = { editing = null },
            onSave = { amount, label, timestamp, category, accountId, receiptUri ->
                viewModel.saveExpense(toEdit, amount, label, timestamp, category, accountId, receiptUri)
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
            settings = exportSettings,
            onSettingsChange = { exportSettings = it },
            onDismiss = { showExport = false },
            onSaveToFile = { settings ->
                showExport = false
                scope.launch {
                    val file = viewModel.buildCsv(settings.options, settings.allTime, settings.startDate, settings.endDate)
                    exportSettings = settings
                    saveExport = true
                    try {
                        saveLauncher.launch(file.fileName)
                    } catch (e: Exception) {
                        saveExport = false
                        Toast.makeText(context, "No file picker available", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onShare = { settings ->
                showExport = false
                scope.launch {
                    val uri = viewModel.csvShareUri(settings.options, settings.allTime, settings.startDate, settings.endDate)
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
        putExtra(Intent.EXTRA_SUBJECT, "Spendora expenses")
        clipData = ClipData.newRawUri("Spendora CSV", uri)
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
                Text(text = "spent this month", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text(text = "Today: " + Format.money(todayTotal), style = MaterialTheme.typography.titleMedium)
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
    selected: Boolean,
    onToggleSelection: () -> Unit,
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
            modifier = Modifier.padding(start = 8.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelection() })
            Spacer(Modifier.width(4.dp))
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
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelection() })
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.label.ifBlank { "(no label)" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = Format.dateTime(expense.timestamp) + " • " +
                        (if (expense.isManual) "Manual" else "SMS") + " • " + expense.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    settings: ExportSettings,
    onSettingsChange: (ExportSettings) -> Unit,
    onDismiss: () -> Unit,
    onSaveToFile: (ExportSettings) -> Unit,
    onShare: (ExportSettings) -> Unit
) {
    var allTime by remember { mutableStateOf(settings.allTime) }
    var startDate by remember { mutableStateOf(settings.startDate ?: "") }
    var endDate by remember { mutableStateOf(settings.endDate ?: "") }
    val options = settings.options

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export CSV") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(selected = !allTime, onClick = { allTime = false }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = !allTime, onClick = { allTime = false })
                    Text("Selected range ($monthLabel / custom dates)")
                }
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(selected = allTime, onClick = { allTime = true }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = allTime, onClick = { allTime = true })
                    Text("All time")
                }
                if (!allTime) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("From (yyyy-MM-dd)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("To (yyyy-MM-dd)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Columns", style = MaterialTheme.typography.labelLarge)
                CsvToggle("Date", options.date) { onSettingsChange(settings.copy(options = options.copy(date = it))) }
                CsvToggle("Time", options.time) { onSettingsChange(settings.copy(options = options.copy(time = it))) }
                CsvToggle("Amount", options.amount) { onSettingsChange(settings.copy(options = options.copy(amount = it))) }
                CsvToggle("Category", options.category) { onSettingsChange(settings.copy(options = options.copy(category = it))) }
                CsvToggle("Account", options.account) { onSettingsChange(settings.copy(options = options.copy(account = it))) }
                CsvToggle("Label", options.label) { onSettingsChange(settings.copy(options = options.copy(label = it))) }
                CsvToggle("Reference", options.reference) { onSettingsChange(settings.copy(options = options.copy(reference = it))) }
                CsvToggle("Source", options.source) { onSettingsChange(settings.copy(options = options.copy(source = it))) }
                CsvToggle("Receipt URI", options.receipt) { onSettingsChange(settings.copy(options = options.copy(receipt = it))) }
                CsvToggle("Include total row", options.total) { onSettingsChange(settings.copy(options = options.copy(total = it))) }
                CsvToggle("Excel BOM", options.bom) { onSettingsChange(settings.copy(options = options.copy(bom = it))) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSaveToFile(
                                settings.copy(
                                    allTime = allTime,
                                    startDate = startDate.ifBlank { null },
                                    endDate = endDate.ifBlank { null }
                                )
                            )
                        }
                    ) { Text("Save to file") }
                    OutlinedButton(
                        onClick = {
                            onShare(
                                settings.copy(
                                    allTime = allTime,
                                    startDate = startDate.ifBlank { null },
                                    endDate = endDate.ifBlank { null }
                                )
                            )
                        }
                    ) { Text("Share") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun CsvToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
