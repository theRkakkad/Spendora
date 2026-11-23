package com.expensetracker.app.sms

import java.security.MessageDigest
import kotlin.math.roundToLong

data class ParsedSms(
    val amount: Double,
    val upiRef: String?
)

/**
 * Pure-Kotlin (no Android classes) parser for Indian bank debit / UPI SMS.
 * Everything runs locally with regular expressions.
 */
object SmsParser {

    /** Fragments matched (case-insensitively) against the SMS sender id, e.g. "VM-HDFCBK". */
    val DEFAULT_SENDERS: List<String> = listOf(
        "SBI", "HDFC", "ICICI", "AXIS", "KOTAK", "PNB", "BOB", "BARODA",
        "CANARA", "CANBNK", "UNION", "UBI", "IDFC", "INDUS", "YESB", "FEDERAL",
        "FEDBNK", "IDBI", "IOB", "UCO", "CENT", "BOI", "MAHB", "AUBANK", "RBL",
        "PAYTM", "AIRTEL", "JIO", "UPI", "BANK", "BNK"
    )

    private val IC = RegexOption.IGNORE_CASE

    private val WHITESPACE = Regex("""\s+""")

    private val OTP = Regex(
        """\b(?:otp|one[\s-]?time\s+password|verification\s+code|security\s+code)\b""", IC
    )

    private val PROMO = Regex(
        """\b(?:offers?|cashback|congratulations|congrats|pre-?approved|apply\s+now|discount|coupon|voucher|lucky\s+draw|win|won)\b|t&c""",
        IC
    )

    /** Future debits, collect requests and failed transactions: money has not actually left. */
    private val NOT_A_DEBIT = Regex(
        """\b(?:will\s+be\s+(?:auto[\s-]?)?debited|to\s+be\s+debited|shall\s+be\s+debited|due\s+on|is\s+due|payment\s+due|overdue|has\s+requested|requested\s+(?:money|payment|rs|inr)|collect\s+request|declined|failed|unsuccessful|could\s+not\s+be\s+processed)\b""",
        IC
    )

    private val DEBIT = Regex(
        """\b(?:debited|debit|spent|paid|sent|withdrawn|transferred|deducted|purchase|dr)\b""", IC
    )

    private val CREDIT = Regex(
        """\b(?:credited|received|deposited|refunded|reversed)\b""", IC
    )

    /** Rs.899.00, Rs 1,250, INR 1,250.50, ₹ 899 */
    private val AMOUNT = Regex(
        """(?<![A-Za-z])(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", IC
    )

    /** SBI style without a currency marker: "debited by 250.0". */
    private val AMOUNT_AFTER_VERB = Regex(
        """\b(?:debited|spent|paid|sent|withdrawn|transferred|deducted)\s+(?:by|for|with|of)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)(?![0-9/\-:])""",
        IC
    )

    /** Text right before an amount that marks it as a balance / limit rather than the spend. */
    private val BALANCE_BEFORE = Regex(
        """(?:bal|balance|limit|lmt)\s*[.:\-]?\s*(?:is|of)?\s*\z""", IC
    )

    private val REF = Regex(
        """\b(?:upi\s*ref(?:erence)?\s*(?:no|number|id)?|ref(?:erence)?\s*(?:no|number|id)?|rrn|utr(?:\s*no)?|(?:txn|transaction)\s*(?:id|no|ref(?:erence)?(?:\s*no)?)|imps(?:\s*ref(?:\s*no)?)?|upi)\s*[.:#\-]*\s*(?:is\s*)?([0-9]{6,20})\b""",
        IC
    )

    /** Axis style: UPI/P2A/400112345678/NAME */
    private val REF_SLASH = Regex("""\bupi/(?:[a-z][a-z0-9]*/)?([0-9]{9,20})""", IC)

    /**
     * Returns the parsed debit, or null when the message is not a completed debit
     * (credit, OTP, promotion, balance-only, future/failed transaction, unknown sender).
     *
     * @param senderKeywords sender must contain one of these; an empty list accepts any sender.
     * @param extraKeywords extra user-defined words that count as "money was debited".
     */
    fun parse(
        sender: String,
        body: String,
        senderKeywords: List<String> = DEFAULT_SENDERS,
        extraKeywords: List<String> = emptyList()
    ): ParsedSms? {
        val text = body.replace(WHITESPACE, " ").trim()
        if (text.isEmpty()) return null
        if (!senderAllowed(sender, senderKeywords)) return null
        if (OTP.containsMatchIn(text)) return null
        if (PROMO.containsMatchIn(text)) return null
        if (NOT_A_DEBIT.containsMatchIn(text)) return null

        val debitIndex = firstDebitIndex(text, extraKeywords)
        if (debitIndex < 0) return null

        val credit = CREDIT.find(text)
        if (credit != null && credit.range.first < debitIndex) return null

        val amount = extractAmount(text) ?: return null
        return ParsedSms(amount = amount, upiRef = extractRef(text))
    }

    fun senderAllowed(sender: String, senderKeywords: List<String>): Boolean {
        val cleaned = senderKeywords.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return true
        val upper = sender.uppercase()
        return cleaned.any { upper.contains(it.uppercase()) }
    }

    private fun firstDebitIndex(text: String, extraKeywords: List<String>): Int {
        var best = DEBIT.find(text)?.range?.first ?: -1
        if (extraKeywords.isNotEmpty()) {
            val lower = text.lowercase()
            for (keyword in extraKeywords) {
                val k = keyword.trim().lowercase()
                if (k.isEmpty()) continue
                val index = lower.indexOf(k)
                if (index >= 0 && (best < 0 || index < best)) best = index
            }
        }
        return best
    }

    private fun extractAmount(text: String): Double? {
        for (match in AMOUNT.findAll(text)) {
            val start = match.range.first
            val before = text.substring(maxOf(0, start - 24), start)
            if (BALANCE_BEFORE.containsMatchIn(before)) continue
            val value = toAmount(match.groupValues[1])
            if (value != null) return value
        }
        val fallback = AMOUNT_AFTER_VERB.find(text) ?: return null
        return toAmount(fallback.groupValues[1])
    }

    private fun toAmount(raw: String): Double? {
        val value = raw.replace(",", "").trimEnd('.').toDoubleOrNull() ?: return null
        return if (value > 0.0 && value < 1_000_000_000.0) value else null
    }

    private fun extractRef(text: String): String? {
        REF.find(text)?.let { return it.groupValues[1] }
        REF_SLASH.find(text)?.let { return it.groupValues[1] }
        return null
    }

    /**
     * Key used to drop duplicates: the UPI reference when there is one, otherwise a
     * hash of amount + timestamp + sender.
     */
    fun dedupeKey(amount: Double, timestamp: Long, sender: String, upiRef: String?): String {
        if (!upiRef.isNullOrBlank()) return "ref:" + upiRef.trim()
        val paise = (amount * 100).roundToLong()
        val raw = "$paise|$timestamp|${sender.trim().uppercase()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder()
        for (i in 0 until 16) {
            hex.append(String.format("%02x", digest[i].toInt() and 0xFF))
        }
        return "hash:$hex"
    }
}
