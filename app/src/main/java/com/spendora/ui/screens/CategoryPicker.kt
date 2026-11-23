package com.spendora.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spendora.data.ExpenseCategory

/**
 * Horizontal, scrollable list of built-in category chips, each with its own icon and color.
 * Both [selected] and the value passed to [onSelect] are [ExpenseCategory.id] strings.
 */
@Composable
fun CategoryPicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ExpenseCategory.all.forEach { category ->
            FilterChip(
                selected = selected == category.id,
                onClick = { onSelect(category.id) },
                leadingIcon = {
                    Icon(
                        imageVector = categoryIcon(category),
                        contentDescription = null,
                        tint = Color(category.color),
                        modifier = Modifier.size(18.dp)
                    )
                },
                label = { Text(category.label) }
            )
        }
    }
}

private fun categoryIcon(category: ExpenseCategory) = when (category) {
    ExpenseCategory.FOOD -> Icons.Filled.Restaurant
    ExpenseCategory.TRAVEL -> Icons.Filled.Flight
    ExpenseCategory.BILLS -> Icons.Filled.ReceiptLong
    ExpenseCategory.SHOPPING -> Icons.Filled.ShoppingCart
    ExpenseCategory.HEALTH -> Icons.Filled.LocalHospital
    ExpenseCategory.FUN -> Icons.Filled.SportsEsports
    ExpenseCategory.OTHER -> Icons.Filled.Category
}
