package com.spendora.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.spendora.data.Expense
import com.spendora.util.Format
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Package-level alias so both `CsvOptions(...)` and `CsvExporter.CsvOptions(...)` work. */
typealias CsvOptions = CsvExporter.CsvOptions

object CsvExporter {

    /** Selects the columns and file options used by [build]. */
    data class CsvOptions(
        val date: Boolean = true,
        val time: Boolean = true,
        val amount: Boolean = true,
        val category: Boolean = true,
        val account: Boolean = true,
        val label: Boolean = true,
        val reference: Boolean = true,
        val source: Boolean = true,
        val receipt: Boolean = false,
        val bom: Boolean = true,
        val total: Boolean = true
    )

    private const val BOM = "﻿"
    private const val EOL = "\r\n"

    private val DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    /** Original, fixed-column export kept for existing callers. */
    fun build(expenses: List<Expense>): String {
        val out = StringBuilder(BOM)
        out.append("Date,Time,Amount,Paid To / Type,Reference No,Month,Source").append(EOL)
        var total = BigDecimal.ZERO
        for (expense in expenses) {
            val at = Format.zoned(expense.timestamp)
            val amount = BigDecimal.valueOf(expense.amount).setScale(2, RoundingMode.HALF_UP)
            total = total.add(amount)
            out.append(
                listOf(
                    DATE.format(at),
                    TIME.format(at),
                    amount.toPlainString(),
                    expense.label,
                    expense.upiRef ?: "",
                    MONTH.format(at),
                    if (expense.isManual) "Manual" else "SMS"
                ).joinToString(",") { escape(it) }
            ).append(EOL)
        }
        out.append(
            listOf("TOTAL", "", total.setScale(2, RoundingMode.HALF_UP).toPlainString(), "", "", "", "")
                .joinToString(",") { escape(it) }
        ).append(EOL)
        return out.toString()
    }

    /**
     * Configurable export: headers and row cells are derived from [options].
     * Optional model fields (category, account, receipt) are read reflectively so this
     * keeps working whether or not the data layer currently stores them.
     */
    fun build(
        expenses: List<Expense>,
        options: CsvOptions,
        accounts: Map<Long, *> = emptyMap<Long, String>()
    ): String {
        val columns = ArrayList<Pair<String, (Expense) -> String>>()
        var amountIndex = -1

        if (options.date) {
            columns += "Date" to { expense -> DATE.format(Format.zoned(expense.timestamp)) }
        }
        if (options.time) {
            columns += "Time" to { expense -> TIME.format(Format.zoned(expense.timestamp)) }
        }
        if (options.amount) {
            amountIndex = columns.size
            columns += "Amount" to { expense ->
                BigDecimal.valueOf(expense.amount).setScale(2, RoundingMode.HALF_UP).toPlainString()
            }
        }
        if (options.category) {
            columns += "Category" to { expense ->
                property(expense, "category", "categoryName", "categoryId")?.toString() ?: ""
            }
        }
        if (options.account) {
            columns += "Account" to { expense ->
                when (val account = property(expense, "accountId", "account")) {
                    is Number -> accounts[account.toLong()]?.let(::accountName) ?: account.toString()
                    else -> account?.let(::accountName) ?: ""
                }
            }
        }
        if (options.label) {
            columns += "Label" to { expense -> expense.label }
        }
        if (options.reference) {
            columns += "Reference No" to { expense -> expense.upiRef ?: "" }
        }
        if (options.source) {
            columns += "Source" to { expense -> if (expense.isManual) "Manual" else "SMS" }
        }
        if (options.receipt) {
            columns += "Receipt" to { expense ->
                property(expense, "receiptUri", "receiptPath", "receipt")?.toString() ?: ""
            }
        }

        val out = StringBuilder(if (options.bom) BOM else "")
        out.append(columns.joinToString(",") { (header, _) -> escape(header) }).append(EOL)
        for (expense in expenses) {
            out.append(columns.joinToString(",") { (_, value) -> escape(value(expense)) }).append(EOL)
        }
        if (options.total) {
            if (columns.isEmpty()) {
                out.append("TOTAL").append(EOL)
            } else {
                val row = MutableList(columns.size) { "" }
                row[0] = "TOTAL"
                if (amountIndex >= 0) {
                    val total = expenses.fold(BigDecimal.ZERO) { sum, expense ->
                        sum.add(BigDecimal.valueOf(expense.amount))
                    }
                    row[amountIndex] = total.setScale(2, RoundingMode.HALF_UP).toPlainString()
                }
                out.append(row.joinToString(",") { escape(it) }).append(EOL)
            }
        }
        return out.toString()
    }

    /** Accepts either a plain account name or a data-layer Account object. */
    private fun accountName(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        else -> try {
            value.javaClass.getMethod("getName").invoke(value) as? String ?: value.toString()
        } catch (e: Exception) {
            value.toString()
        }
    }

    /** Reads an optional property added to Expense by newer data-layer versions. */
    private fun property(expense: Expense, vararg names: String): Any? {
        for (name in names) {
            val getter = "get" + name.replaceFirstChar { it.uppercaseChar() }
            val value = try {
                expense.javaClass.getMethod(getter).invoke(expense)
            } catch (e: Exception) {
                null
            }
            if (value != null) return value
        }
        return null
    }

    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    /** Writes the CSV to the location the user picked in the system file picker. */
    fun writeToUri(context: Context, uri: Uri, csv: String): Boolean = try {
        val stream = context.contentResolver.openOutputStream(uri, "wt")
        if (stream == null) {
            false
        } else {
            stream.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            true
        }
    } catch (e: Exception) {
        false
    }

    /** Writes the CSV into the app cache and returns a shareable content:// uri. */
    fun writeToCache(context: Context, fileName: String, csv: String): Uri {
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeBytes(csv.toByteArray(Charsets.UTF_8))
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }
}
