package com.spendora.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spendora.SpendoraApp
import com.spendora.backup.BackupManager
import com.spendora.data.Account
import com.spendora.data.Budget
import com.spendora.data.Expense
import com.spendora.data.ExpenseCategory
import com.spendora.data.SplitShare
import com.spendora.data.SettingsStore
import com.spendora.export.CsvExporter
import com.spendora.notify.NotificationHelper
import com.spendora.notify.ReminderScheduler
import com.spendora.sms.SmsProcessor
import com.spendora.util.Format
import com.spendora.util.Insights
import com.spendora.util.InsightsEngine
import com.spendora.util.SmsRuleTester
import com.spendora.util.SmsTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

data class DashboardFilters(
    val category: String = "ALL",
    val source: String = "ALL",
    val status: String = "ALL",
    val minAmount: String = "",
    val maxAmount: String = ""
)

data class DashboardState(
    val month: YearMonth,
    val pending: List<Expense> = emptyList(),
    val saved: List<Expense> = emptyList(),
    val monthSaved: List<Expense> = emptyList(),
    val monthTotal: Double = 0.0,
    val todayTotal: Double = 0.0,
    val pendingTotal: Double = 0.0,
    val hasAnySavedInMonth: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val budgetLimit: Double = 0.0
)

class CsvFile(val fileName: String, val content: String)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SpendoraApp
    private val repository = app.repository
    private val settings = app.settings
    private val backupManager = BackupManager(app, repository, settings)

    // ---- Profile -------------------------------------------------------------------------

    private val userNameFlow: Flow<String?> = settings.userName

    /** null while loading, "" when the name has not been set yet (first launch). */
    val userName: StateFlow<String?> =
        userNameFlow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setUserName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { settings.setUserName(trimmed) }
    }

    // ---- Dashboard -----------------------------------------------------------------------

    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search.asStateFlow()

    private val _filters = MutableStateFlow(DashboardFilters())
    val filters: StateFlow<DashboardFilters> = _filters.asStateFlow()

    val accounts: StateFlow<List<Account>> = repository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val dashboardBase: StateFlow<DashboardState> = combine(
        repository.observeAll(),
        repository.observeAccounts(),
        repository.observeBudgets(),
        selectedMonth,
        _filters
    ) { all, accountList, budgets, month, filter ->

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val pending = all.filter { it.status == Expense.STATUS_PENDING }
        val saved = all.filter { it.status == Expense.STATUS_SAVED }
        val savedInMonth = saved.filter { it.timestamp in Format.monthStartMillis(month) until Format.monthEndMillis(month) }
        val min = filter.minAmount.toDoubleOrNull()
        val max = filter.maxAmount.toDoubleOrNull()

        val visible = savedInMonth.filter { expense ->
            val matchesCategory = filter.category == "ALL" || expense.category == filter.category
            val matchesSource = filter.source == "ALL" ||
                (filter.source == "MANUAL" && expense.isManual) ||
                (filter.source == "SMS" && !expense.isManual)
            val matchesStatus = filter.status == "ALL" || expense.status == filter.status
            val matchesMin = min == null || expense.amount >= min
            val matchesMax = max == null || expense.amount <= max
            matchesCategory && matchesSource && matchesStatus && matchesMin && matchesMax
        }

        DashboardState(
            month = month,
            pending = pending,
            saved = visible,
            monthSaved = savedInMonth,
            monthTotal = savedInMonth.sumOf { it.amount },
            todayTotal = saved.filter { it.timestamp in todayStart until todayEnd }.sumOf { it.amount },
            pendingTotal = pending.sumOf { it.amount },
            hasAnySavedInMonth = savedInMonth.isNotEmpty(),
            accounts = accountList,
            budgetLimit = budgets.firstOrNull { it.month == month.toString() }?.limit ?: 0.0
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardState(month = YearMonth.now())
    )

    val dashboard: StateFlow<DashboardState> = combine(dashboardBase, _search) { state, query ->
        val q = query.trim()
        if (q.isEmpty()) {
            state
        } else {
            state.copy(
                saved = state.saved.filter { expense ->
                    expense.label.contains(q, ignoreCase = true) ||
                        expense.sender.contains(q, ignoreCase = true) ||
                        expense.category.contains(q, ignoreCase = true)
                }
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DashboardState(month = YearMonth.now())
    )

    val recentLabels: StateFlow<List<String>> = repository.observeRecentLabels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allExpenses: StateFlow<List<Expense>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val insights: StateFlow<Insights> = allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
        .let { expenses ->
            combine(expenses, _search) { all, _ ->
                InsightsEngine.build(all.filter { it.status == Expense.STATUS_SAVED })
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsEngine.build(emptyList()))
        }

    val shares: StateFlow<List<SplitShare>> = repository.observeShares()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    fun previousMonth() { selectedMonth.value = selectedMonth.value.minusMonths(1) }
    fun nextMonth() { selectedMonth.value = selectedMonth.value.plusMonths(1) }
    fun currentMonth() { selectedMonth.value = YearMonth.now() }
    fun setSearch(query: String) { _search.value = query }

    fun setCategoryFilter(value: String) { _filters.value = _filters.value.copy(category = value) }
    fun setSourceFilter(value: String) { _filters.value = _filters.value.copy(source = value) }
    fun setStatusFilter(value: String) { _filters.value = _filters.value.copy(status = value) }
    fun setMinAmountFilter(value: String) { _filters.value = _filters.value.copy(minAmount = value) }
    fun setMaxAmountFilter(value: String) { _filters.value = _filters.value.copy(maxAmount = value) }
    fun clearFilters() { _filters.value = DashboardFilters() }

    fun toggleSelection(id: Long) {
        _selection.value = if (_selection.value.contains(id)) _selection.value - id else _selection.value + id
    }

    fun clearSelection() { _selection.value = emptySet() }

    fun bulkDelete() {
        val ids = _selection.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteByIds(ids)
            ids.forEach { NotificationHelper.cancel(app, it) }
            NotificationHelper.updateSummary(app)
            clearSelection()
        }
    }

    fun bulkSetCategory(categoryId: String) {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val all = repository.observeAll().first()
            repository.updateAll(all.filter { it.id in ids }.map { it.copy(category = categoryId) })
            clearSelection()
        }
    }

    fun bulkSetAccount(accountId: Long) {
        val ids = _selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val all = repository.observeAll().first()
            repository.updateAll(all.filter { it.id in ids }.map { it.copy(accountId = accountId) })
            clearSelection()
        }
    }

    // ---- Expenses ------------------------------------------------------------------------

    fun addManual(
        amount: Double,
        label: String,
        timestamp: Long,
        category: String = ExpenseCategory.OTHER.id,
        accountId: Long = 1L,
        receiptUri: String? = null
    ) {
        viewModelScope.launch {
            repository.insert(
                Expense(
                    amount = amount,
                    label = label.trim(),
                    timestamp = timestamp,
                    sender = "",
                    upiRef = null,
                    userName = userName.value ?: "",
                    status = Expense.STATUS_SAVED,
                    isManual = true,
                    dedupeKey = "manual:" + UUID.randomUUID().toString(),
                    category = category,
                    accountId = accountId,
                    receiptUri = receiptUri
                )
            )
        }
    }

    /** Saves edits; a PENDING expense becomes SAVED and its notification goes away. */
    fun saveExpense(
        expense: Expense,
        amount: Double,
        label: String,
        timestamp: Long,
        category: String = expense.category,
        accountId: Long = expense.accountId,
        receiptUri: String? = expense.receiptUri
    ) {
        viewModelScope.launch {
            repository.update(
                expense.copy(
                    amount = amount,
                    label = label.trim(),
                    timestamp = timestamp,
                    status = Expense.STATUS_SAVED,
                    category = category,
                    accountId = accountId,
                    receiptUri = receiptUri
                )
            )
            NotificationHelper.cancel(app, expense.id)
            NotificationHelper.updateSummary(app)
        }
    }

    fun delete(expense: Expense) {
        viewModelScope.launch {
            repository.delete(expense.id)
            NotificationHelper.cancel(app, expense.id)
            NotificationHelper.updateSummary(app)
        }
    }

    // ---- Budgets -------------------------------------------------------------------------

    fun setBudget(month: YearMonth, limit: Double) {
        viewModelScope.launch { repository.upsertBudget(Budget(month = month.toString(), limit = limit)) }
    }

    // ---- Splits --------------------------------------------------------------------------

    fun addShare(expenseId: Long, person: String, amount: Double) {
        if (person.isBlank() || amount <= 0.0) return
        viewModelScope.launch {
            repository.insertShare(SplitShare(expenseId = expenseId, person = person.trim(), amount = amount))
        }
    }

    fun toggleShare(share: SplitShare) {
        viewModelScope.launch { repository.updateShare(share.copy(settled = !share.settled)) }
    }

    // ---- SMS rule tester -----------------------------------------------------------------

    private val _smsTestResult = MutableStateFlow<SmsTestResult?>(null)
    val smsTestResult: StateFlow<SmsTestResult?> = _smsTestResult.asStateFlow()

    fun testSmsRule(sender: String, body: String) {
        viewModelScope.launch {
            val snapshot = settings.snapshot()
            _smsTestResult.value = SmsRuleTester.test(sender, body, snapshot.senders, snapshot.keywords)
        }
    }

    /** Called whenever the app comes to the foreground. */
    fun onAppResumed() {
        viewModelScope.launch {
            ReminderScheduler.schedule(app, settings.snapshot().reminderMinutes, replace = false)
            NotificationHelper.refreshAll(app, alert = false)
        }
    }

    // ---- CSV export ----------------------------------------------------------------------

    suspend fun buildCsv(allTime: Boolean): CsvFile = buildCsv(CsvExporter.CsvOptions(), allTime, null, null)

    suspend fun buildCsv(
        options: CsvExporter.CsvOptions,
        allTime: Boolean,
        startDate: String?,
        endDate: String?
    ): CsvFile = withContext(Dispatchers.IO) {
        val month = selectedMonth.value
        val zone = ZoneId.systemDefault()
        val rows = when {
            allTime -> repository.getAllSaved()
            startDate != null || endDate != null -> {
                val start = parseDay(startDate)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
                    ?: Format.monthStartMillis(month)
                val end = parseDay(endDate)?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
                    ?: Format.monthEndMillis(month)
                repository.getSavedBetween(start, end)
            }
            else -> repository.getSavedBetween(Format.monthStartMillis(month), Format.monthEndMillis(month))
        }
        val name = if (allTime) {
            "spendora-all-time.csv"
        } else {
            "spendora-" + DateTimeFormatter.ofPattern("yyyy-MM", Locale.ENGLISH).format(month) + ".csv"
        }
        val accountsById = repository.observeAccounts().first().associateBy { it.id }
        CsvFile(name, CsvExporter.build(rows, options, accountsById))
    }

    private fun parseDay(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrNull()
    }

    suspend fun writeCsv(uri: Uri, allTime: Boolean): Boolean =
        writeCsv(uri, CsvExporter.CsvOptions(), allTime, null, null)

    suspend fun writeCsv(
        uri: Uri,
        options: CsvExporter.CsvOptions,
        allTime: Boolean,
        startDate: String?,
        endDate: String?
    ): Boolean = withContext(Dispatchers.IO) {
        CsvExporter.writeToUri(app, uri, buildCsv(options, allTime, startDate, endDate).content)
    }

    suspend fun csvShareUri(allTime: Boolean): Uri? = csvShareUri(CsvExporter.CsvOptions(), allTime, null, null)

    suspend fun csvShareUri(
        options: CsvExporter.CsvOptions,
        allTime: Boolean,
        startDate: String?,
        endDate: String?
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val file = buildCsv(options, allTime, startDate, endDate)
            CsvExporter.writeToCache(app, file.fileName, file.content)
        } catch (e: Exception) {
            null
        }
    }

    // ---- Settings ------------------------------------------------------------------------

    val reminderMinutes: StateFlow<Int> = settings.reminderMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsStore.DEFAULT_REMINDER_MINUTES)

    val sendersText: StateFlow<String> = settings.sendersText
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsStore.DEFAULT_SENDERS_TEXT)

    val keywordsText: StateFlow<String> = settings.keywordsText
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setReminderMinutes(minutes: Int) {
        viewModelScope.launch {
            settings.setReminderMinutes(minutes)
            ReminderScheduler.schedule(app, minutes, replace = true)
        }
    }

    fun saveSendersText(text: String) {
        viewModelScope.launch { settings.setSendersText(text) }
    }

    fun resetSenders() {
        viewModelScope.launch { settings.setSendersText(SettingsStore.DEFAULT_SENDERS_TEXT) }
    }

    fun saveKeywordsText(text: String) {
        viewModelScope.launch { settings.setKeywordsText(text) }
    }

    /**
     * Pushes a fake bank SMS through exactly the same path as a real one
     * (parser, dedupe, Room, notification, popup).
     */
    fun simulateTestSms(onResult: (SmsProcessor.Outcome) -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val reference = (1..12).joinToString("") { Random.nextInt(0, 10).toString() }
                .replaceFirst(Regex("^0"), "4")
            val date = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ENGLISH).format(Format.zoned(now))
            val body = "Sent Rs.499.00 From HDFC Bank A/C *1234 To TEST MERCHANT On $date " +
                "Ref $reference Not You? Call 18002586161/SMS BLOCK UPI to 7308080808"
            val outcome = withContext(Dispatchers.IO) {
                SmsProcessor.process(app, "VM-HDFCBK", body, now)
            }
            onResult(outcome)
        }
    }

    // ---- Encrypted backup ----------------------------------------------------------------

    private val _backupResult = MutableStateFlow<String?>(null)
    val backupResult: StateFlow<String?> = _backupResult.asStateFlow()

    fun exportBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            _backupResult.value = if (backupManager.export(uri, passphrase)) {
                "Encrypted backup saved"
            } else {
                "Backup failed: ${backupManager.lastError() ?: "unknown error"}"
            }
        }
    }

    fun importBackup(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { backupManager.import(uri, passphrase) }
            _backupResult.value = if (result.expenses > 0 || result.accounts > 0 || result.budgets > 0 || result.shares > 0) {
                "Restored ${result.expenses} expenses, ${result.accounts} accounts, " +
                    "${result.budgets} budgets and ${result.shares} splits"
            } else {
                "Import failed: ${backupManager.lastError() ?: "no data restored"}"
            }
        }
    }
}
