package com.expensetracker.app.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.expensetracker.app.data.Expense
import com.expensetracker.app.util.Format
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.format.DateTimeFormatter
import java.util.Locale

object CsvExporter {

    private const val BOM = "﻿"
    private const val EOL = "\r\n"

    private val DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    /** Builds the whole CSV document (UTF-8 BOM first, so Excel reads the text correctly). */
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
