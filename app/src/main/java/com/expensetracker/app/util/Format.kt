package com.expensetracker.app.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {

    private val INDIA = Locale("en", "IN")
    private val DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH)
    private val DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    /** ₹1,25,000.50 (Indian digit grouping, no trailing zeros). */
    fun money(amount: Double): String {
        val format = NumberFormat.getNumberInstance(INDIA)
        format.minimumFractionDigits = 0
        format.maximumFractionDigits = 2
        return "₹" + format.format(amount)
    }

    /** 899 or 899.5 - plain text for editing in a text field. */
    fun plainAmount(amount: Double): String {
        val rounded = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
        if (rounded.signum() == 0) return "0"
        return rounded.stripTrailingZeros().toPlainString()
    }

    /** Parses user input such as "1,250.50" or "₹ 899". Returns null if not a positive number. */
    fun parseAmount(text: String): Double? {
        val cleaned = text.replace(",", "").replace("₹", "").trim()
        val value = cleaned.toDoubleOrNull() ?: return null
        return if (value > 0.0 && value < 1_000_000_000.0) value else null
    }

    fun zoned(timestamp: Long): ZonedDateTime =
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())

    fun dateTime(timestamp: Long): String = DATE_TIME.format(zoned(timestamp))

    fun date(timestamp: Long): String = DATE.format(zoned(timestamp))

    fun time(timestamp: Long): String = TIME.format(zoned(timestamp))

    fun month(month: YearMonth): String = MONTH.format(month)

    fun monthStartMillis(month: YearMonth): Long =
        month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun monthEndMillis(month: YearMonth): Long = monthStartMillis(month.plusMonths(1))
}
