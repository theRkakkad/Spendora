package com.spendora.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class Budget(@PrimaryKey val month: String, val limit: Double)
