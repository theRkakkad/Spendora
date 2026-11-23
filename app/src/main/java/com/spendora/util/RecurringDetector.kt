package com.spendora.util

import com.spendora.data.Expense
import java.util.Locale
import kotlin.math.roundToInt

/** A repeated, saved expense (for example a subscription) summarised over time. */
data class RecurringGroup(
    val label: String,
    val count: Int,
    val averageAmount: Double,
    val lastTimestamp: Long,
    val cadenceDays: Int
)

/** Finds repeat payments using only expenses the user has confirmed as saved. */
object RecurringDetector {

    private const val MILLIS_PER_DAY = 86_400_000L

    fun detect(expenses: List<Expense>): List<RecurringGroup> {
        val saved = expenses.filter { it.status == Expense.STATUS_SAVED && it.label.isNotBlank() }
        return saved.groupBy { it.label.trim().lowercase(Locale.ROOT) }
            .mapNotNull { (label, items) ->
                if (items.size < 2) return@mapNotNull null
                val ordered = items.sortedBy { it.timestamp }
                val span = ordered.last().timestamp - ordered.first().timestamp
                val cadence = span.toDouble() / (items.size - 1) / MILLIS_PER_DAY
                RecurringGroup(
                    label = label,
                    count = items.size,
                    averageAmount = items.sumOf { it.amount } / items.size,
                    lastTimestamp = ordered.last().timestamp,
                    cadenceDays = cadence.roundToInt().coerceAtLeast(0)
                )
            }
            .sortedWith(compareByDescending<RecurringGroup> { it.count }
                .thenByDescending { it.averageAmount })
    }
}
