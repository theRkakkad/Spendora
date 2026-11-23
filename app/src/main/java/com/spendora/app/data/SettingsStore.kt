package com.spendora.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spendora.app.sms.SmsParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class SettingsSnapshot(
    val userName: String,
    val reminderMinutes: Int,
    val senders: List<String>,
    val keywords: List<String>
)

class SettingsStore(context: Context) {

    private val store = context.applicationContext.settingsDataStore

    val userName: Flow<String> = store.data.map { it[KEY_NAME] ?: "" }

    val reminderMinutes: Flow<Int> = store.data.map { it[KEY_REMINDER] ?: DEFAULT_REMINDER_MINUTES }

    /** One sender fragment per line, e.g. "HDFC". Empty text means "accept any sender". */
    val sendersText: Flow<String> = store.data.map { it[KEY_SENDERS] ?: DEFAULT_SENDERS_TEXT }

    /** Extra words (one per line) that mark an SMS as a debit, on top of the built-in ones. */
    val keywordsText: Flow<String> = store.data.map { it[KEY_KEYWORDS] ?: "" }

    suspend fun snapshot(): SettingsSnapshot {
        val prefs = store.data.first()
        return SettingsSnapshot(
            userName = prefs[KEY_NAME] ?: "",
            reminderMinutes = prefs[KEY_REMINDER] ?: DEFAULT_REMINDER_MINUTES,
            senders = splitList(prefs[KEY_SENDERS] ?: DEFAULT_SENDERS_TEXT),
            keywords = splitList(prefs[KEY_KEYWORDS] ?: "")
        )
    }

    suspend fun setUserName(name: String) {
        store.edit { it[KEY_NAME] = name.trim() }
    }

    suspend fun setReminderMinutes(minutes: Int) {
        store.edit { it[KEY_REMINDER] = minutes }
    }

    suspend fun setSendersText(text: String) {
        store.edit { it[KEY_SENDERS] = splitList(text).joinToString("\n") }
    }

    suspend fun setKeywordsText(text: String) {
        store.edit { it[KEY_KEYWORDS] = splitList(text).joinToString("\n") }
    }

    companion object {
        const val DEFAULT_REMINDER_MINUTES = 30
        val REMINDER_OPTIONS = listOf(15, 30, 60, 120)
        val DEFAULT_SENDERS_TEXT: String = SmsParser.DEFAULT_SENDERS.joinToString("\n")

        private val KEY_NAME = stringPreferencesKey("user_name")
        private val KEY_REMINDER = intPreferencesKey("reminder_minutes")
        private val KEY_SENDERS = stringPreferencesKey("senders")
        private val KEY_KEYWORDS = stringPreferencesKey("keywords")

        fun splitList(text: String): List<String> =
            text.split('\n', ',', ';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
    }
}
