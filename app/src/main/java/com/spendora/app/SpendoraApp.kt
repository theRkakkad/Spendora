package com.spendora.app

import android.app.Application
import android.content.Context
import com.spendora.app.data.AppDatabase
import com.spendora.app.data.ExpenseRepository
import com.spendora.app.data.SettingsStore
import com.spendora.app.notify.NotificationHelper

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

val Context.app: SpendoraApp
    get() = applicationContext as SpendoraApp
