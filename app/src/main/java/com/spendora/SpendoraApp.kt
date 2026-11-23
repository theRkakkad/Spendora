package com.spendora

import android.app.Application
import android.content.Context
import com.spendora.data.AppDatabase
import com.spendora.data.ExpenseRepository
import com.spendora.data.SettingsStore
import com.spendora.notify.NotificationHelper

/**
 * Application class. Acts as a tiny service locator so receivers, workers,
 * activities and view models all share one database and one settings store.
 */
class SpendoraApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: ExpenseRepository by lazy { ExpenseRepository(database.expenseDao()) }
    val settings: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}

val Context.spendoraApp: SpendoraApp
    get() = applicationContext as SpendoraApp
