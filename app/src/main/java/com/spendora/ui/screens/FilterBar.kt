package com.spendora.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val SOURCE_OPTIONS = listOf("ALL", "MANUAL", "SMS")
private val STATUS_OPTIONS = listOf("ALL", "PENDING", "SAVED")

/**
 * Reusable expense filters. Source and status use the raw uppercase values persisted by the app;
 * category uses [ExpenseCategory.id][com.spendora.data.ExpenseCategory.id].
 */
@Composable
fun FilterBar(
    category: String,
    source: String,
    status: String,
    minAmount: String,
    maxAmount: String,
    onCategory: (String) -> Unit,
    onSource: (String) -> Unit,
    onStatus: (String) -> Unit,
    onMinAmount: (String) -> Unit,
    onMaxAmount: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Category",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CategoryPicker(selected = category, onSelect = onCategory)

        Text(
            text = "Source",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SOURCE_OPTIONS.forEach { option ->
                FilterChip(
                    selected = source == option,
                    onClick = { onSource(option) },
                    label = { Text(option) }
                )
            }
        }

        Text(
            text = "Status",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            STATUS_OPTIONS.forEach { option ->
                FilterChip(
                    selected = status == option,
                    onClick = { onStatus(option) },
                    label = { Text(option) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = minAmount,
                onValueChange = onMinAmount,
                label = { Text("Min ₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = maxAmount,
                onValueChange = onMaxAmount,
                label = { Text("Max ₹") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}
