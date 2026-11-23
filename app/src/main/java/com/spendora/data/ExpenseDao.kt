package com.spendora.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    /** Returns the new row id, or -1 when the dedupeKey already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(expense: Expense): Long

    @Update
    suspend fun update(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Expense?

    @Query("SELECT * FROM expenses ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE status = 'PENDING' ORDER BY timestamp DESC, id DESC")
    suspend fun getPending(): List<Expense>

    @Query(
        "SELECT * FROM expenses WHERE status = 'SAVED' " +
            "AND timestamp >= :startMillis AND timestamp < :endMillis " +
            "ORDER BY timestamp ASC, id ASC"
    )
    suspend fun getSavedBetween(startMillis: Long, endMillis: Long): List<Expense>

    @Query("SELECT * FROM expenses WHERE status = 'SAVED' ORDER BY timestamp ASC, id ASC")
    suspend fun getAllSaved(): List<Expense>

    @Query(
        "SELECT label FROM expenses WHERE status = 'SAVED' AND label != '' " +
            "GROUP BY label ORDER BY MAX(timestamp) DESC LIMIT :maxCount"
    )
    fun observeRecentLabels(maxCount: Int): Flow<List<String>>

    // ---- Accounts ------------------------------------------------------------------------

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC, id ASC")
    fun observeAccounts(): Flow<List<Account>>

    @Insert
    suspend fun insertAccount(account: Account): Long

    @Update
    suspend fun updateAccount(account: Account)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun accountCount(): Int

    // ---- Budgets -------------------------------------------------------------------------

    @Query("SELECT * FROM budgets ORDER BY month ASC")
    fun observeBudgets(): Flow<List<Budget>>

    @Upsert
    suspend fun upsertBudget(budget: Budget)

    @Query("SELECT * FROM budgets WHERE month = :month LIMIT 1")
    suspend fun getBudget(month: String): Budget?

    // ---- Split shares --------------------------------------------------------------------

    @Query("SELECT * FROM split_shares ORDER BY id ASC")
    fun observeShares(): Flow<List<SplitShare>>

    @Query("SELECT * FROM split_shares WHERE expenseId = :expenseId")
    suspend fun getSharesForExpense(expenseId: Long): List<SplitShare>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShare(share: SplitShare): Long

    @Update
    suspend fun updateShare(share: SplitShare)

    @Query("DELETE FROM split_shares WHERE expenseId = :expenseId")
    suspend fun deleteSharesForExpense(expenseId: Long)

    // ---- Bulk expense operations ---------------------------------------------------------

    @Query("DELETE FROM expenses WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Update
    suspend fun updateAll(expenses: List<Expense>)
}
