package com.spendora.util

import com.spendora.data.Expense
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class MerchantTotal(val name: String, val total: Double, val count: Int)

data class WeekdayTotal(val day: String, val total: Double)

data class MonthTotal(val month: String, val total: Double)

data class Insights(
    val merchants: List<MerchantTotal>,
    val weekdays: List<WeekdayTotal>,
    val months: List<MonthTotal>,
    val total: Double,
    val average: Double
)

/** Aggregates confirmed (SAVED) spending for the insights screen. */
object InsightsEngine {

    private val MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT)

    fun build(expenses: List<Expense>): Insights {
        val saved = expenses.filter { it.status == Expense.STATUS_SAVED }

        val merchants = saved
            .filter { it.label.isNotBlank() }
            .groupBy { it.label.trim() }
            .map { (name, items) -> MerchantTotal(name, items.sumOf { it.amount }, items.size) }
            .sortedWith(compareByDescending<MerchantTotal> { it.total }
                .thenBy { it.name.lowercase(Locale.ROOT) })

        val weekdays = saved
            .groupBy { expense ->
                Format.zoned(expense.timestamp).dayOfWeek
                    .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            }
            .map { (day, items) -> WeekdayTotal(day, items.sumOf { it.amount }) }
            .sortedWith(compareByDescending<WeekdayTotal> { it.total }.thenBy { it.day })

        val months = saved
            .groupBy { expense -> MONTH_KEY.format(Format.zoned(expense.timestamp)) }
            .map { (month, items) -> MonthTotal(month, items.sumOf { it.amount }) }
            .sortedBy { it.month }

        val total = saved.sumOf { it.amount }
        val average = if (saved.isEmpty()) 0.0 else total / saved.size
        return Insights(merchants, weekdays, months, total, average)
    }
}
