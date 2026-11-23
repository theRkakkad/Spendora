package com.spendora.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "split_shares",
    foreignKeys = [
        ForeignKey(
            entity = Expense::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("expenseId")]
)
data class SplitShare(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val person: String,
    val amount: Double,
    val settled: Boolean = false
)
