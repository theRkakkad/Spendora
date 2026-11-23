package com.spendora.backup

import android.content.Context
import android.net.Uri
import com.spendora.data.Expense
import com.spendora.data.ExpenseRepository
import com.spendora.data.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.suspendCoroutine

data class ImportResult(val expenses: Int, val accounts: Int, val budgets: Int, val shares: Int)

/**
 * Passphrase-protected local backup. The JSON payload is derived with
 * PBKDF2WithHmacSHA256 and encrypted with AES/GCM, so a backup can be moved to
 * another device without depending on this device's Android Keystore.
 *
 * File layout: "SPENDBAK1" | salt (16 bytes) | IV (12 bytes) | ciphertext + tag.
 */
class BackupManager(
    private val context: Context,
    private val repository: ExpenseRepository,
    private val settings: SettingsStore
) {

    private var error: String? = null

    fun lastError(): String? = error

    suspend fun export(uri: Uri, passphrase: String): Boolean {
        error = null
        val problem = passphraseProblem(passphrase)
        if (problem != null) {
            error = problem
            return false
        }
        return try {
            val plain = buildBackupJson().toString()
            val secret = encrypt(plain, passphrase)
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("Cannot open backup destination")
            stream.use { it.write(secret) }
            true
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            false
        }
    }

    suspend fun import(uri: Uri, passphrase: String): ImportResult {
        error = null
        val problem = passphraseProblem(passphrase)
        if (problem != null) {
            error = problem
            return ImportResult(0, 0, 0, 0)
        }
        return try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open backup file")
            val bytes = stream.use { it.readBytes() }
            val json = JSONObject(decrypt(bytes, passphrase))
            val expenseArray = json.optJSONArray("expenses")
            val expenses = if (expenseArray == null) {
                emptyList()
            } else {
                (0 until expenseArray.length()).mapNotNull { index ->
                    expenseArray.optJSONObject(index)?.let(::expenseFromJson)
                }
            }

            // Account/budget/share writes must go through repository methods supplied by
            // the data agent. Reaching into the DAO indirectly is deliberately not done;
            // without those methods there is no safe restore path, so report an empty result.
            if (!hasRepositoryMethod("insertAccount") ||
                !hasRepositoryMethod("upsertBudget") ||
                !hasRepositoryMethod("insertShare")
            ) {
                error = "Backup restore requires insertAccount/upsertBudget/insertShare on ExpenseRepository."
                return ImportResult(0, 0, 0, 0)
            }

            repository.observeAll().first().forEach { repository.delete(it.id) }
            var insertedExpenses = 0
            for (expense in expenses) {
                if (repository.insert(expense) >= 0) insertedExpenses++
            }

            val insertedAccounts = importBeans("insertAccount", json.optJSONArray("accounts"))
            val insertedBudgets = importBeans("upsertBudget", json.optJSONArray("budgets"))
            val insertedShares = importBeans("insertShare", json.optJSONArray("shares"))
            restoreSettings(json.optJSONObject("settings"))

            ImportResult(insertedExpenses, insertedAccounts, insertedBudgets, insertedShares)
        } catch (e: AEADBadTagException) {
            error = "Wrong passphrase or the backup file has been tampered with."
            ImportResult(0, 0, 0, 0)
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            ImportResult(0, 0, 0, 0)
        }
    }

    // ---- JSON -----------------------------------------------------------------------------

    private suspend fun buildBackupJson(): JSONObject {
        val snapshot = settings.snapshot()
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("expenses", JSONArray(repository.observeAll().first().map(::expenseToJson)))
            put("accounts", JSONArray(observeBeans("observeAllAccounts", "observeAccounts").map(::beanToJson)))
            put("budgets", JSONArray(observeBeans("observeAllBudgets", "observeBudgets").map(::beanToJson)))
            put("shares", JSONArray(observeBeans("observeAllShares", "observeShares").map(::beanToJson)))
            put("settings", JSONObject().apply {
                put("userName", snapshot.userName)
                put("reminderMinutes", snapshot.reminderMinutes)
                put("senders", JSONArray(snapshot.senders))
                put("keywords", JSONArray(snapshot.keywords))
            })
        }
    }

    private fun expenseToJson(expense: Expense): JSONObject {
        val json = JSONObject().apply {
            put("id", expense.id)
            put("amount", expense.amount)
            put("label", expense.label)
            put("timestamp", expense.timestamp)
            put("sender", expense.sender)
            putOpt("upiRef", expense.upiRef)
            put("userName", expense.userName)
            put("status", expense.status)
            put("isManual", expense.isManual)
            put("dedupeKey", expense.dedupeKey)
        }
        addExtraProperties(expense, json)
        return json
    }

    private fun expenseFromJson(json: JSONObject): Expense {
        val timestamp = json.optLong("timestamp")
        val dedupeKey = json.optString("dedupeKey").ifBlank {
            "import:" + timestamp + "-" + UUID.randomUUID()
        }
        return Expense(
            id = json.optLong("id"),
            amount = json.optDouble("amount"),
            label = json.optString("label"),
            timestamp = timestamp,
            sender = json.optString("sender"),
            upiRef = json.optString("upiRef").takeIf { it.isNotEmpty() },
            userName = json.optString("userName"),
            status = json.optString("status", Expense.STATUS_SAVED),
            isManual = json.optBoolean("isManual"),
            dedupeKey = dedupeKey
        )
    }

    /** Reads account/budget/share lists only when the data layer actually exposes them. */
    private suspend fun observeBeans(vararg methodNames: String): List<Any> {
        for (name in methodNames) {
            val method = repository.javaClass.methods.firstOrNull {
                it.name == name && it.parameterCount == 0
            } ?: continue
            val value = try {
                method.invoke(repository)
            } catch (e: Exception) {
                null
            }
            val list: List<*>? = when (value) {
                is Flow<*> -> try {
                    @Suppress("UNCHECKED_CAST")
                    value.first() as List<*>?
                } catch (e: Exception) {
                    null
                }
                is List<*> -> value
                else -> null
            }
            if (list != null) return list.filter { it != null } as List<Any>
        }
        return emptyList()
    }

    private fun beanToJson(value: Any): JSONObject {
        val json = JSONObject()
        addExtraProperties(value, json)
        return json
    }

    private fun addExtraProperties(value: Any, json: JSONObject) {
        for (method in value.javaClass.methods) {
            if (method.parameterCount != 0 || method.name == "getClass") continue
            val key = propertyName(method) ?: continue
            if (json.has(key)) continue
            val property = try {
                method.invoke(value)
            } catch (e: Exception) {
                continue
            } ?: continue
            try {
                json.put(key, jsonValue(property))
            } catch (e: Exception) {
                // Unsupported property types simply stay out of the backup.
            }
        }
    }

    private fun propertyName(method: Method): String? = when {
        method.name.startsWith("get") && method.name.length > 3 &&
            method.returnType != Void.TYPE ->
            method.name.substring(3).replaceFirstChar { it.lowercase(Locale.ROOT) }
        method.name.startsWith("is") && method.name.length > 2 &&
            method.returnType == java.lang.Boolean.TYPE ->
            method.name.substring(2).replaceFirstChar { it.lowercase(Locale.ROOT) }
        else -> null
    }

    private fun jsonValue(value: Any): Any = when (value) {
        is String, is Number, is Boolean, is JSONObject, is JSONArray -> value
        is Enum<*> -> value.name
        is Iterable<*> -> JSONArray(value.map { item -> item?.let(::jsonValue) ?: JSONObject.NULL })
        is Array<*> -> JSONArray(value.map { item -> item?.let(::jsonValue) ?: JSONObject.NULL })
        else -> beanToJson(value)
    }

    private suspend fun restoreSettings(json: JSONObject?) {
        if (json == null) return
        val name = json.optString("userName")
        if (name.isNotEmpty()) settings.setUserName(name)
        settings.setReminderMinutes(json.optInt("reminderMinutes", SettingsStore.DEFAULT_REMINDER_MINUTES))
        settings.setSendersText(joinStrings(json.optJSONArray("senders")))
        settings.setKeywordsText(joinStrings(json.optJSONArray("keywords")))
    }

    private fun joinStrings(array: JSONArray?): String = buildString {
        if (array == null) return@buildString
        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (value.isEmpty()) continue
            if (isNotEmpty()) append('\n')
            append(value)
        }
    }

    // ---- Repository reflection (models may arrive with the data agent) --------------------

    private fun hasRepositoryMethod(name: String): Boolean = repository.javaClass.methods.any { method ->
        method.name == name && method.parameterCount in 1..2 &&
            (method.parameterCount == 1 || method.parameterTypes.last() == Continuation::class.java)
    }

    private suspend fun importBeans(methodName: String, array: JSONArray?): Int {
        if (array == null) return 0
        val method = repository.javaClass.methods.firstOrNull { candidate ->
            candidate.name == methodName && candidate.parameterCount in 1..2 &&
                (candidate.parameterCount == 1 ||
                    candidate.parameterTypes.last() == Continuation::class.java)
        } ?: return 0

        var count = 0
        for (i in 0 until array.length()) {
            val json = array.optJSONObject(i) ?: continue
            val instance = instantiate(method.parameterTypes[0], json) ?: continue
            val result = invokeRepository(method, instance)
            if (countsAsImported(result)) count++
        }
        return count
    }

    private suspend fun invokeRepository(method: Method, argument: Any?): Any? = suspendCoroutine { continuation ->
        try {
            if (method.parameterCount == 2) {
                method.invoke(repository, argument, object : Continuation<Any?> {
                    override val context: CoroutineContext = continuation.context
                    override fun resumeWith(result: Result<Any?>) {
                        continuation.resumeWith(result)
                    }
                })
            } else {
                continuation.resumeWith(Result.success(method.invoke(repository, argument)))
            }
        } catch (e: Exception) {
            continuation.resumeWith(Result.failure(e))
        }
    }

    private fun countsAsImported(result: Any?): Boolean = when (result) {
        null, is Unit -> true
        is Long -> result >= 0
        is Int -> result >= 0
        is Boolean -> result
        else -> true
    }

    private fun instantiate(type: Class<*>, json: JSONObject): Any? {
        val constructor: Constructor<*> = type.declaredConstructors.minByOrNull { it.parameterCount }
            ?: return null
        constructor.isAccessible = true
        if (constructor.parameterCount == 0) return constructor.newInstance()

        val usedKeys = HashSet<String>()
        val arguments = Array<Any?>(constructor.parameterCount) { index ->
            val parameter = constructor.parameters[index]
            val named = parameter.name.takeIf { it.isNotBlank() && it != "arg$index" }
            val raw = if (named != null && json.has(named)) {
                usedKeys += named
                json.get(named)
            } else {
                findCompatibleValue(json, constructor.parameterTypes[index], usedKeys)
            }
            coerce(raw, constructor.parameterTypes[index])
        }
        return try {
            constructor.newInstance(*arguments)
        } catch (e: Exception) {
            null
        }
    }

    private fun findCompatibleValue(
        json: JSONObject,
        type: Class<*>,
        usedKeys: MutableSet<String>
    ): Any? {
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in usedKeys) continue
            val value = json.opt(key) ?: continue
            if (matchesType(value, type)) {
                usedKeys += key
                return value
            }
        }
        return null
    }

    private fun matchesType(value: Any, type: Class<*>): Boolean = when (type) {
        java.lang.String::class.java -> value is String
        java.lang.Boolean.TYPE -> value is Boolean
        java.lang.Byte.TYPE, java.lang.Short.TYPE, java.lang.Integer.TYPE,
        java.lang.Long.TYPE, java.lang.Float.TYPE, java.lang.Double.TYPE -> value is Number
        else -> type.isAssignableFrom(value.javaClass) || value is JSONObject
    }

    @Suppress("UNCHECKED_CAST")
    private fun coerce(value: Any?, type: Class<*>): Any? {
        if (value == null || value === JSONObject.NULL) return defaultValue(type)
        val text = value.toString()
        return when (type) {
            java.lang.String::class.java -> text
            java.lang.Boolean.TYPE -> value as? Boolean ?: text.toBoolean()
            java.lang.Byte.TYPE -> (value as? Number)?.toByte() ?: text.toByteOrNull() ?: 0
            java.lang.Short.TYPE -> (value as? Number)?.toShort() ?: text.toShortOrNull() ?: 0
            java.lang.Integer.TYPE -> (value as? Number)?.toInt() ?: text.toIntOrNull() ?: 0
            java.lang.Long.TYPE -> (value as? Number)?.toLong() ?: text.toLongOrNull() ?: 0L
            java.lang.Float.TYPE -> (value as? Number)?.toFloat() ?: text.toFloatOrNull() ?: 0f
            java.lang.Double.TYPE -> (value as? Number)?.toDouble() ?: text.toDoubleOrNull() ?: 0.0
            java.lang.Character.TYPE -> text.firstOrNull() ?: '\u0000'
            else -> when {
                type.isEnum -> {
                    val constants = type.enumConstants as? Array<out Enum<*>> ?: emptyArray()
                    constants.firstOrNull { it.name.equals(text, ignoreCase = true) }
                }
                value is JSONObject -> instantiate(type, value)
                else -> defaultValue(type)
            }
        }
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }

    // ---- Passphrase encryption -------------------------------------------------------------

    private fun passphraseProblem(passphrase: String): String? = when {
        passphrase.isBlank() -> "A backup passphrase is required."
        passphrase.length < MIN_PASSPHRASE_LENGTH ->
            "Backup passphrase must be at least $MIN_PASSPHRASE_LENGTH characters."
        else -> null
    }

    private fun encrypt(plain: String, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_BYTES)
        val iv = ByteArray(IV_BYTES)
        val random = SecureRandom()
        random.nextBytes(salt)
        random.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))

        val out = ByteArrayOutputStream(MAGIC.size + salt.size + iv.size + body.size)
        out.write(MAGIC)
        out.write(salt)
        out.write(iv)
        out.write(body)
        return out.toByteArray()
    }

    private fun decrypt(data: ByteArray, passphrase: String): String {
        val minimum = MAGIC.size + SALT_BYTES + IV_BYTES + GCM_TAG_BITS / 8
        if (data.size < minimum || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw IllegalArgumentException("Not a Spendora backup file (invalid header).")
        }
        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES)
        val iv = data.copyOfRange(MAGIC.size + SALT_BYTES, MAGIC.size + SALT_BYTES + IV_BYTES)
        val body = data.copyOfRange(MAGIC.size + SALT_BYTES + IV_BYTES, data.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(
            passphrase.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            AES_KEY_BITS
        )
        return try {
            val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            SecretKeySpec(hash, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private companion object {
        const val FORMAT = "spendora-encrypted-backup"
        const val VERSION = 1
        const val MIN_PASSPHRASE_LENGTH = 6
        const val SALT_BYTES = 16
        const val IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val AES_KEY_BITS = 256
        const val PBKDF2_ITERATIONS = 120_000
        val MAGIC: ByteArray = "SPENDBAK1".toByteArray(Charsets.US_ASCII)
    }
}
