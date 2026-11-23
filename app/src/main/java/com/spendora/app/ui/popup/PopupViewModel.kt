package com.spendora.app.ui.popup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spendora.app.SpendoraApp
import com.spendora.app.data.Expense
import com.spendora.app.notify.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PopupViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SpendoraApp
    private val repository = app.repository

    private val _expense = MutableStateFlow<Expense?>(null)
    val expense: StateFlow<Expense?> = _expense.asStateFlow()

    /** True once the popup has nothing (more) to do and the activity should close. */
    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    val recentLabels: StateFlow<List<String>> = repository.observeRecentLabels()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun load(expenseId: Long) {
        viewModelScope.launch {
            val found = if (expenseId > 0L) repository.getById(expenseId) else null
            if (found == null || found.status != Expense.STATUS_PENDING) {
                // Already saved or deleted: make sure no stale notification is left behind.
                if (expenseId > 0L) NotificationHelper.cancel(app, expenseId)
                NotificationHelper.updateSummary(app)
                _finished.value = true
            } else {
                _expense.value = found
            }
        }
    }

    fun save(amount: Double, label: String) {
        val current = _expense.value ?: return
        viewModelScope.launch {
            repository.update(
                current.copy(
                    amount = amount,
                    label = label.trim(),
                    status = Expense.STATUS_SAVED
                )
            )
            NotificationHelper.cancel(app, current.id)
            NotificationHelper.updateSummary(app)
            _finished.value = true
        }
    }
}
