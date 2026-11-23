package com.spendora.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
}
