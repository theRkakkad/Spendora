package com.spendora.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spendora.SpendoraApp
import com.spendora.data.Expense
import com.spendora.data.SettingsStore
import com.spendora.export.CsvExporter
import com.spendora.notify.NotificationHelper
import com.spendora.notify.ReminderScheduler
import com.spendora.sms.SmsProcessor
import com.spendora.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

data class DashboardState(
    val month: YearMonth,
    val pending: List<Expense> = emptyList(),
    val saved: List<Expense> = emptyList(),
    val monthTotal: Double = 0.0,
    val todayTotal: Double = 0.0,
    val pendingTotal: Double = 0.0,
    val hasAnySavedInMonth: Boolean = false
)

class CsvFile(val fileName: String, val content: String)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SpendoraApp
    private val repository = app.repository
    private val settings = app.settings

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

    val dashboard: StateFlow<DashboardState> =
        combine(repository.observeAll(), selectedMonth, _search) { all, month, query ->
            buildDashboard(all, month, query)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            DashboardState(month = YearMonth.now())
        )

    val recentLabels: StateFlow<List<String>> = repository.observeRecentLabels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun buildDashboard(all: List<Expense>, month: YearMonth, query: String): DashboardState {
        val monthStart = Format.monthStartMillis(month)
        val monthEnd = Format.monthEndMillis(month)
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val todayEnd = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val pending = all.filter { it.status == Expense.STATUS_PENDING }
        val saved = all.filter { it.status == Expense.STATUS_SAVED }
        val savedInMonth = saved.filter { it.timestamp in monthStart until monthEnd }
        val q = query.trim()
        val visible = if (q.isEmpty()) savedInMonth else savedInMonth.filter { it.label.contains(q, ignoreCase = true) }

        return DashboardState(
            month = month,
            pending = pending,
            saved = visible,
            monthTotal = savedInMonth.sumOf { it.amount },
            todayTotal = saved.filter { it.timestamp in todayStart until todayEnd }.sumOf { it.amount },
            pendingTotal = pending.sumOf { it.amount },
            hasAnySavedInMonth = savedInMonth.isNotEmpty()
        )
    }

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }

    fun currentMonth() {
        selectedMonth.value = YearMonth.now()
    }

    fun setSearch(query: String) {
        _search.value = query
    }

    fun addManual(amount: Double, label: String, timestamp: Long) {
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
                    dedupeKey = "manual:" + UUID.randomUUID().toString()
                )
            )
        }
    }

    /** Saves edits; a PENDING expense becomes SAVED and its notification goes away. */
    fun saveExpense(expense: Expense, amount: Double, label: String, timestamp: Long) {
        viewModelScope.launch {
            repository.update(
                expense.copy(
                    amount = amount,
                    label = label.trim(),
                    timestamp = timestamp,
                    status = Expense.STATUS_SAVED
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

    /** Called whenever the app comes to the foreground. */
    fun onAppResumed() {
        viewModelScope.launch {
            ReminderScheduler.schedule(app, settings.snapshot().reminderMinutes, replace = false)
            NotificationHelper.refreshAll(app, alert = false)
        }
    }

    // ---- CSV export ----------------------------------------------------------------------

    suspend fun buildCsv(allTime: Boolean): CsvFile = withContext(Dispatchers.IO) {
        val month = selectedMonth.value
        val rows = if (allTime) {
            repository.getAllSaved()
        } else {
            repository.getSavedBetween(Format.monthStartMillis(month), Format.monthEndMillis(month))
        }
        val name = if (allTime) {
            "spendora-all-time.csv"
        } else {
            "spendora-" + DateTimeFormatter.ofPattern("yyyy-MM", Locale.ENGLISH).format(month) + ".csv"
        }
        CsvFile(name, CsvExporter.build(rows))
    }

    suspend fun writeCsv(uri: Uri, allTime: Boolean): Boolean = withContext(Dispatchers.IO) {
        CsvExporter.writeToUri(app, uri, buildCsv(allTime).content)
    }

    suspend fun csvShareUri(allTime: Boolean): Uri? = withContext(Dispatchers.IO) {
        try {
            val file = buildCsv(allTime)
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
}
