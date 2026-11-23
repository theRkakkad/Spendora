package com.spendora.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.spendora.data.Expense
import com.spendora.spendoraApp
import com.spendora.ui.MainActivity
import com.spendora.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** Glance home-screen widget showing today's saved spend with a shortcut into the app. */
class SpendoraWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // provideGlance is a suspend function, so the total can be loaded before the first
        // composition. Any failure is kept out of the widget UI so it still renders.
        val total = try {
            todayTotal(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read today's total for the widget", e)
            0.0
        }

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(GlanceTheme.colors.widgetBackground)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Spendora",
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = Format.money(total),
                        style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    Text(
                        text = "spent today",
                        style = TextStyle(fontSize = 12.sp),
                        maxLines = 1,
                    )
                    Spacer(modifier = GlanceModifier.height(12.dp))
                    Button(
                        text = "Add expense",
                        onClick = actionStartActivity(
                            Intent(context, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                putExtra(MainActivity.EXTRA_OPEN_ADD_EXPENSE, true)
                            }
                        ),
                        modifier = GlanceModifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "SpendoraWidget"
    }
}

/**
 * Sum of expenses saved (not merely detected) between local midnight and the next local midnight.
 * Shared with the quick settings tile; Room reads are moved off the main thread.
 */
internal suspend fun todayTotal(context: Context): Double = withContext(Dispatchers.IO) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    context.spendoraApp.repository.observeAll()
        .first()
        .filter { expense ->
            expense.status == Expense.STATUS_SAVED &&
                expense.timestamp >= start &&
                expense.timestamp < end
        }
        .sumOf { it.amount }
}
