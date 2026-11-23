package com.spendora.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendora.util.Format
import kotlin.math.roundToInt

/** Monthly spending limit editor with a capped progress ring. */
@Composable
fun BudgetScreen(
    month: String,
    limit: Double,
    monthTotal: Double,
    onSave: (Double) -> Unit,
    onBack: () -> Unit
) {
    var limitText by remember(limit) { mutableStateOf(Format.plainAmount(limit)) }

    val parsed = limitText.replace(",", "").replace("₹", "").trim().toDoubleOrNull()
    val valid = parsed != null && parsed >= 0.0 && parsed < 1_000_000_000.0
    val hasLimit = limit > 0.0
    val overLimit = hasLimit && monthTotal > limit
    val progress = when {
        !hasLimit -> 0f
        else -> (monthTotal / limit).toFloat().coerceIn(0f, 1f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budget - $month") },
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = month,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier.size(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 12.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(progress * 100).roundToInt()}%",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "of limit used",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Spent: ${Format.money(monthTotal)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = when {
                            !hasLimit -> "Set a monthly limit to start tracking."
                            overLimit -> "${Format.money(monthTotal - limit)} over your ${Format.money(limit)} limit"
                            else -> "${Format.money(limit - monthTotal)} left for $month"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (overLimit) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Monthly limit",
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { limitText = it },
                        label = { Text("Limit (₹)") },
                        singleLine = true,
                        isError = limitText.isNotBlank() && !valid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row {
                        Button(
                            onClick = { parsed?.let { onSave(it) } },
                            enabled = valid,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save limit") }
                    }
                }
            }
        }
    }
}
