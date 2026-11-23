package com.spendora.util

import com.spendora.sms.SmsParser

data class SmsTestResult(
    val accepted: Boolean,
    val amount: Double?,
    val upiRef: String?,
    val reason: String
)

/**
 * Runs a sample SMS through the real parser and explains why it was accepted or
 * rejected, so users can tune sender and keyword rules with immediate feedback.
 */
object SmsRuleTester {

    private val IC = RegexOption.IGNORE_CASE

    private val WHITESPACE = Regex("""\s+""")

    private val OTP = Regex(
        """\b(?:otp|one[\s-]?time\s+password|verification\s+code|security\s+code)\b""", IC
    )

    private val PROMO = Regex(
        """\b(?:offers?|cashback|congratulations|congrats|pre-?approved|apply\s+now|discount|coupon|voucher|lucky\s+draw|win|won)\b|t&c""",
        IC
    )

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

    private val AMOUNT = Regex(
        """(?:(?:rs\.?|inr|₹)\s*[0-9]|\b(?:debited|spent|paid|sent|withdrawn|transferred|deducted)\s+(?:by|for|with|of)?\s*[0-9])""",
        IC
    )

    fun test(
        sender: String,
        body: String,
        senders: List<String>,
        keywords: List<String>
    ): SmsTestResult {
        val text = body.replace(WHITESPACE, " ").trim()
        if (text.isEmpty()) {
            return SmsTestResult(false, null, null, "Ignored: empty message.")
        }
        if (!SmsParser.senderAllowed(sender, senders)) {
            return SmsTestResult(
                false, null, null,
                "Sender not allowed by the current rules (no match in sender list)."
            )
        }
        if (OTP.containsMatchIn(text)) {
            return SmsTestResult(false, null, null, "Ignored: OTP / verification code message.")
        }
        if (PROMO.containsMatchIn(text)) {
            return SmsTestResult(false, null, null, "Ignored: promotional / offer message.")
        }
        if (NOT_A_DEBIT.containsMatchIn(text)) {
            return SmsTestResult(
                false, null, null,
                "Ignored: not a completed debit (future, failed or collect request)."
            )
        }

        val parsed = SmsParser.parse(sender, body, senders, keywords)
        if (parsed == null) {
            val reason = when {
                CREDIT.containsMatchIn(text) && !DEBIT.containsMatchIn(text) ->
                    "Ignored: credit message, not a debit."
                CREDIT.containsMatchIn(text) ->
                    "Ignored: credit mentioned before the debit."
                !AMOUNT.containsMatchIn(text) ->
                    "Ignored: no amount found in the message."
                else ->
                    "Ignored: no debit keyword found; add one in SMS keywords."
            }
            return SmsTestResult(false, null, null, reason)
        }

        return SmsTestResult(
            accepted = true,
            amount = parsed.amount,
            upiRef = parsed.upiRef,
            reason = "Accepted: debit of ${Format.money(parsed.amount)} detected."
        )
    }
}
