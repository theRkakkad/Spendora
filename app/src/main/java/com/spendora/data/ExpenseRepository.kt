package com.spendora.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val dao: ExpenseDao) {

    fun observeAll(): Flow<List<Expense>> = dao.observeAll()

    fun observeRecentLabels(): Flow<List<String>> = observeRecentLabels(12)

    fun observeRecentLabels(maxCount: Int): Flow<List<String>> = dao.observeRecentLabels(maxCount)

    /** Returns the new id, or -1 if an expense with the same dedupeKey already exists. */
    suspend fun insert(expense: Expense): Long = dao.insert(expense)

    suspend fun update(expense: Expense) = dao.update(expense)

    suspend fun delete(id: Long) = deleteById(id)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun getById(id: Long): Expense? = dao.getById(id)

    suspend fun getPending(): List<Expense> = dao.getPending()

    suspend fun getSavedBetween(startMillis: Long, endMillis: Long): List<Expense> =
        dao.getSavedBetween(startMillis, endMillis)

    suspend fun getAllSaved(): List<Expense> = dao.getAllSaved()

    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)

    suspend fun updateAll(expenses: List<Expense>) = dao.updateAll(expenses)

    // ---- Accounts ------------------------------------------------------------------------

    fun observeAccounts(): Flow<List<Account>> = dao.observeAccounts()

    suspend fun insertAccount(account: Account): Long = dao.insertAccount(account)

    suspend fun updateAccount(account: Account) = dao.updateAccount(account)

    suspend fun deleteAccount(id: Long) = dao.deleteAccount(id)

    suspend fun accountCount(): Int = dao.accountCount()

    /** Inserts the standard accounts only on a fresh database. */
    suspend fun seedDefaults() {
        if (dao.accountCount() != 0) return
        listOf(
            Account(name = "Cash", type = "CASH", color = 0xFF4CAF50L, sortOrder = 0),
            Account(name = "UPI", type = "UPI", color = 0xFF2196F3L, sortOrder = 1),
            Account(name = "Card", type = "CARD", color = 0xFF9C27B0L, sortOrder = 2),
            Account(name = "Bank", type = "BANK", color = 0xFF607D8BL, sortOrder = 3)
        ).forEach { dao.insertAccount(it) }
    }

    // ---- Budgets -------------------------------------------------------------------------

    fun observeBudgets(): Flow<List<Budget>> = dao.observeBudgets()

    suspend fun upsertBudget(budget: Budget) = dao.upsertBudget(budget)

    suspend fun getBudget(month: String): Budget? = dao.getBudget(month)

    // ---- Split shares --------------------------------------------------------------------

    fun observeShares(): Flow<List<SplitShare>> = dao.observeShares()

    suspend fun getSharesForExpense(expenseId: Long): List<SplitShare> =
        dao.getSharesForExpense(expenseId)

    suspend fun insertShare(share: SplitShare): Long = dao.insertShare(share)

    suspend fun updateShare(share: SplitShare) = dao.updateShare(share)

    suspend fun deleteSharesForExpense(expenseId: Long) = dao.deleteSharesForExpense(expenseId)
}
