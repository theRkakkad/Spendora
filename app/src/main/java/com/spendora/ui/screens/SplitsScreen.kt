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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import com.spendora.data.Expense
import com.spendora.data.SplitShare
import com.spendora.util.Format

/** Manage split shares and their settled state. */
@Composable
fun SplitsScreen(
    shares: List<SplitShare>,
    expenses: List<Expense>,
    onToggle: (SplitShare) -> Unit,
    onAdd: (expenseId: Long, person: String, amount: Double) -> Unit,
    onBack: () -> Unit
) {
    var expenseIdText by remember { mutableStateOf("") }
    var person by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    val expenseId = expenseIdText.trim().toLongOrNull()
    val amount = Format.parseAmount(amountText)
    val canAdd = expenseId != null && person.isNotBlank() && amount != null
    val labels = remember(expenses) { expenses.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split expenses") },
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Add a share",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = expenseIdText,
                        onValueChange = { expenseIdText = it },
                        label = { Text("Expense ID") },
                        placeholder = { Text("e.g. 12") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = person,
                        onValueChange = { person = it },
                        label = { Text("Person") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (₹)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (expenseId != null && amount != null && person.isNotBlank()) {
                                onAdd(expenseId, person.trim(), amount)
                                expenseIdText = ""
                                person = ""
                                amountText = ""
                            }
                        },
                        enabled = canAdd,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add share") }
                }
            }

            Text(
                text = "Shares (${shares.size})",
                style = MaterialTheme.typography.titleMedium
            )

            if (shares.isEmpty()) {
                Text(
                    text = "No split shares yet. Add one above to track who owes what.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                shares.forEach { share ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = share.settled,
                                onCheckedChange = { onToggle(share) }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = share.person,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val sourceLabel = labels[share.expenseId]?.label
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "Expense #${share.expenseId}"
                                Text(
                                    text = sourceLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = Format.money(share.amount),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
