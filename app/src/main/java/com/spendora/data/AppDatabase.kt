package com.spendora.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Expense::class, Account::class, Budget::class, SplitShare::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao

    companion object {

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // New columns on expenses (defaults keep existing rows valid).
                db.execSQL("ALTER TABLE expenses ADD COLUMN category TEXT NOT NULL DEFAULT 'other'")
                db.execSQL("ALTER TABLE expenses ADD COLUMN accountId INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE expenses ADD COLUMN receiptUri TEXT")
                db.execSQL("ALTER TABLE expenses ADD COLUMN splitWith TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN splitSettled INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `budgets` (" +
                        "`month` TEXT NOT NULL, " +
                        "`limit` REAL NOT NULL, " +
                        "PRIMARY KEY(`month`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `split_shares` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`expenseId` INTEGER NOT NULL, " +
                        "`person` TEXT NOT NULL, " +
                        "`amount` REAL NOT NULL, " +
                        "`settled` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_split_shares_expenseId` " +
                        "ON `split_shares` (`expenseId`)"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "spendora.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
