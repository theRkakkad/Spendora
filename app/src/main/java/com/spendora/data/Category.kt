package com.spendora.data

/** Built-in expense categories. `id` is what gets persisted in [Expense.category]. */
enum class ExpenseCategory(val id: String, val label: String, val color: Long) {
    FOOD("food", "Food", 0xFFFF7043L),
    TRAVEL("travel", "Travel", 0xFF42A5F5L),
    BILLS("bills", "Bills", 0xFF66BB6AL),
    SHOPPING("shopping", "Shopping", 0xFFAB47BCL),
    HEALTH("health", "Health", 0xFF26A69AL),
    FUN("fun", "Fun", 0xFFFFCA28L),
    OTHER("other", "Other", 0xFF9E9E9EL);

    companion object {
        val all: List<ExpenseCategory> = values().toList()

        /** Falls back to [OTHER] for unknown or missing ids. */
        fun from(id: String?): ExpenseCategory =
            all.firstOrNull { it.id == id } ?: OTHER
    }
}
