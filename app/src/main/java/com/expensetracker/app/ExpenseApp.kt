package com.expensetracker.app

import android.app.Application
import android.content.Context
import com.expensetracker.app.data.AppDatabase
import com.expensetracker.app.data.ExpenseRepository
import com.expensetracker.app.data.SettingsStore
import com.expensetracker.app.notify.NotificationHelper

/**
 * Application class. Acts as a tiny service locator so receivers, workers,
 * activities and view models all share one database and one settings store.
 */
class ExpenseApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val repository: ExpenseRepository by lazy { ExpenseRepository(database.expenseDao()) }
    val settings: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
    }
}

val Context.app: ExpenseApp
    get() = applicationContext as ExpenseApp
