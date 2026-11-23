package com.spendora.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["dedupeKey"], unique = true),
        Index(value = ["timestamp"])
    ]
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val label: String = "",
    val timestamp: Long,
    val sender: String = "",
    val upiRef: String? = null,
    val userName: String = "",
    val status: String = STATUS_PENDING,
    val isManual: Boolean = false,
    /** "ref:<upi ref>" or a hash of amount+timestamp+sender; unique, used to drop duplicate SMS. */
    val dedupeKey: String
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SAVED = "SAVED"
    }
}
