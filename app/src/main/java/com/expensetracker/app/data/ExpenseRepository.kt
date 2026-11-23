package com.expensetracker.app.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val dao: ExpenseDao) {

    fun observeAll(): Flow<List<Expense>> = dao.observeAll()

    fun observeRecentLabels(): Flow<List<String>> = dao.observeRecentLabels(12)

    /** Returns the new id, or -1 if an expense with the same dedupeKey already exists. */
    suspend fun insert(expense: Expense): Long = dao.insert(expense)

    suspend fun update(expense: Expense) = dao.update(expense)

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun getById(id: Long): Expense? = dao.getById(id)

    suspend fun getPending(): List<Expense> = dao.getPending()

    suspend fun getSavedBetween(startMillis: Long, endMillis: Long): List<Expense> =
        dao.getSavedBetween(startMillis, endMillis)

    suspend fun getAllSaved(): List<Expense> = dao.getAllSaved()
}
