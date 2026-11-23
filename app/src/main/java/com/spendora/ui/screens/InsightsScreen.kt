package com.spendora.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spendora.data.Expense
import com.spendora.util.Format
import com.spendora.util.InsightsEngine
import com.spendora.util.RecurringDetector

/** Spending insights built from the supplied expenses. */
@Composable
fun InsightsScreen(expenses: List<Expense>, onBack: () -> Unit) {
    val insights = remember(expenses) { InsightsEngine.build(expenses) }
    val recurring = remember(expenses) { RecurringDetector.detect(expenses) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (expenses.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No expenses yet. Insights will appear once you have a few payments.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                InsightCard(title = "Overview") {
                    Text(
                        text = Format.money(insights.total),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Total across ${expenses.size} expenses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Average per expense: ${Format.money(insights.average)}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                if (recurring.isNotEmpty()) {
                    InsightCard(title = "Recurring payments") {
                        recurring.forEach { group ->
                            Text(
                                text = "${group.label} - ${group.count} times, ${Format.money(group.averageAmount)} each",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Every ${group.cadenceDays} days - last on ${Format.date(group.lastTimestamp)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                if (insights.merchants.isNotEmpty()) {
                    val max = insights.merchants.maxOf { it.total }
                    InsightCard(title = "Top merchants") {
                        insights.merchants.forEach { merchant ->
                            RankedRow(
                                label = merchant.name.toString().ifBlank { "(no label)" },
                                detail = "${merchant.count} payments",
                                amount = merchant.total,
                                fraction = barFraction(merchant.total, max)
                            )
                        }
                    }
                }

                if (insights.weekdays.isNotEmpty()) {
                    val max = insights.weekdays.maxOf { it.total }
                    InsightCard(title = "Spending by weekday") {
                        insights.weekdays.forEach { weekday ->
                            RankedRow(
                                label = weekday.day.toString(),
                                detail = null,
                                amount = weekday.total,
                                fraction = barFraction(weekday.total, max)
                            )
                        }
                    }
                }

                if (insights.months.isNotEmpty()) {
                    val max = insights.months.maxOf { it.total }
                    InsightCard(title = "Month by month") {
                        insights.months.forEach { month ->
                            RankedRow(
                                label = month.month.toString(),
                                detail = null,
                                amount = month.total,
                                fraction = barFraction(month.total, max)
                            )
                        }
                    }
                }

                if (recurring.isEmpty() && insights.merchants.isEmpty() &&
                    insights.weekdays.isEmpty() && insights.months.isEmpty()
                ) {
                    Text(
                        text = "Not enough data for detailed patterns yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

private fun barFraction(value: Double, max: Double): Float =
    if (max <= 0.0 || !value.isFinite()) 0f else (value / max).toFloat().coerceIn(0f, 1f)

@Composable
private fun InsightCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun RankedRow(
    label: String,
    detail: String?,
    amount: Double,
    fraction: Float
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = Format.money(amount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
