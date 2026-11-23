package com.spendora.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    private fun assertDebit(sender: String, body: String, amount: Double, ref: String?) {
        val parsed = SmsParser.parse(sender, body)
        assertNotNull("Expected a debit to be detected in: $body", parsed)
        assertEquals(amount, parsed!!.amount, 0.001)
        assertEquals(ref, parsed.upiRef)
    }

    private fun assertIgnored(sender: String, body: String) {
        assertNull("Expected message to be ignored: $body", SmsParser.parse(sender, body))
    }

    // ---- Positive samples: bank UPI debit formats ---------------------------------------

    @Test
    fun sbi_upi_debit_without_currency_marker() {
        assertDebit(
            "JD-SBIUPI",
            "Dear UPI user A/C X1234 debited by 250.0 on date 01Jan24 trf to MR RAMESH " +
                "Refno 400112345678. If not u? call 1800111109. -SBI",
            250.0,
            "400112345678"
        )
    }

    @Test
    fun hdfc_upi_sent() {
        assertDebit(
            "VM-HDFCBK",
            "Sent Rs.500.00 From HDFC Bank A/C *1234 To SWIGGY On 01/01/24 Ref 400112345678 " +
                "Not You? Call 18002586161/SMS BLOCK UPI to 7308080808",
            500.0,
            "400112345678"
        )
    }

    @Test
    fun icici_debited_with_merchant_credited() {
        assertDebit(
            "AX-ICICIB",
            "ICICI Bank Acct XX123 debited for Rs 899.00 on 01-Jan-24; SWIGGY credited. " +
                "UPI:400112345679. Call 18002662 for dispute. SMS BLOCK 123 to 9215676766.",
            899.0,
            "400112345679"
        )
    }

    @Test
    fun axis_inr_with_slash_reference() {
        assertDebit(
            "AX-AXISBK",
            "INR 1,250.00 debited from A/c no. XX1234 on 01-01-24 12:30:45 " +
                "UPI/P2A/400112345680/JOHN DOE. Not you? SMS BLOCKUPI Cust ID to 919951860002 - Axis Bank",
            1250.0,
            "400112345680"
        )
    }

    @Test
    fun kotak_upi_sent() {
        assertDebit(
            "VK-KOTAKB",
            "Sent Rs.500.00 from Kotak Bank AC X1234 to abc@okaxis on 01-01-24.UPI Ref 400112345681. " +
                "Not you, https://kotak.com/KBANKT/Fraud",
            500.0,
            "400112345681"
        )
    }

    @Test
    fun pnb_debit_ignores_balance_amount() {
        assertDebit(
            "AD-PNBSMS",
            "A/c XX1234 debited INR 500.00 Dt 01-01-24 12:30 thru UPI:400112345682. " +
                "Bal INR 4500.00 Not U? Fwd this SMS to 9264092640 to block UPI.-PNB",
            500.0,
            "400112345682"
        )
    }

    @Test
    fun bob_dr_format() {
        assertDebit(
            "AX-BOBTXN",
            "Rs.350.00 Dr. from A/C XXXXXX1234 and Cr. to merchant@ybl. Ref:400112345683. " +
                "AvlBal:Rs5000.00(2024:01:01 12:30:45). Not you? Call 18005700 -BOB",
            350.0,
            "400112345683"
        )
    }

    @Test
    fun rupee_symbol_with_space_and_ref_no() {
        assertDebit(
            "VK-KOTAKB",
            "₹ 899 paid to Zomato via UPI. Ref No 400155556666. -Kotak Bank",
            899.0,
            "400155556666"
        )
    }

    @Test
    fun card_spend_without_reference() {
        assertDebit(
            "AX-ICICIB",
            "INR 1,250.00 spent on ICICI Bank Card XX1234 on 01-Jan-24 at AMAZON. " +
                "Avl Lmt: INR 50,000.00. To dispute, call 18002662/SMS BLOCK 1234 to 9215676766",
            1250.0,
            null
        )
    }

    @Test
    fun indian_digit_grouping_with_paise() {
        assertDebit(
            "VM-HDFCBK",
            "Rs 1,25,000.50 debited from HDFC Bank A/c XX1234 on 01-01-24. UPI Ref No 400177770000.",
            125000.5,
            "400177770000"
        )
    }

    @Test
    fun balance_listed_before_the_debit_is_skipped() {
        assertDebit(
            "JD-SBIUPI",
            "Avl Bal Rs.2,000.00. Rs.150.00 debited from A/c XX1234 via UPI Ref 400199998888 -SBI",
            150.0,
            "400199998888"
        )
    }

    // ---- Negative samples -----------------------------------------------------------------

    @Test
    fun credit_message_is_ignored() {
        assertIgnored(
            "VM-HDFCBK",
            "Dear Customer, Rs.5,000.00 credited to your A/c XX1234 on 01-01-24 by UPI ref " +
                "400112345684. Avl Bal Rs.12,345.00 -HDFC Bank"
        )
    }

    @Test
    fun sbi_credit_message_is_ignored() {
        assertIgnored(
            "JD-SBIUPI",
            "Your a/c XX1234 is credited by Rs.2000.00 on 01Jan24 transfer from RAMESH " +
                "Ref No 400112345685 -SBI"
        )
    }

    @Test
    fun received_money_mentioning_sent_is_ignored() {
        assertIgnored(
            "AX-AXISBK",
            "You have received Rs.500.00 sent by RAHUL via UPI Ref 400112345686 - Axis Bank"
        )
    }

    @Test
    fun otp_message_is_ignored() {
        assertIgnored(
            "VM-HDFCBK",
            "123456 is the OTP for your txn of INR 1,250.00 at AMAZON on HDFC Bank Card ending 1234. " +
                "Do not share OTP with anyone."
        )
    }

    @Test
    fun one_time_password_message_is_ignored() {
        assertIgnored(
            "JD-SBIUPI",
            "Your one time password for UPI registration is 482913. Valid for 10 mins. -SBI"
        )
    }

    @Test
    fun promotional_message_is_ignored() {
        assertIgnored(
            "VM-HDFCBK",
            "Get 10% cashback up to Rs.200 on bills paid via PayZapp. Offer valid till 31 Jan. T&C apply."
        )
    }

    @Test
    fun balance_only_message_is_ignored() {
        assertIgnored(
            "JD-SBIUPI",
            "Your A/c XX1234 has Avl Bal of Rs.10,500.25 as on 01-01-24. -SBI"
        )
    }

    @Test
    fun upcoming_autopay_is_ignored() {
        assertIgnored(
            "VM-HDFCBK",
            "Rs.499.00 will be debited from your a/c XX1234 on 05-Jan-24 for Netflix autopay mandate."
        )
    }

    @Test
    fun collect_request_is_ignored() {
        assertIgnored(
            "AX-ICICIB",
            "RAHUL has requested Rs.500.00 from you via UPI. Open your UPI app to pay. -ICICI Bank"
        )
    }

    // ---- Sender and keyword lists ---------------------------------------------------------

    @Test
    fun personal_number_is_ignored_with_default_sender_list() {
        assertIgnored("+919876543210", "I paid Rs 500 to the plumber for you")
    }

    @Test
    fun empty_sender_list_accepts_any_sender() {
        val parsed = SmsParser.parse(
            sender = "+919876543210",
            body = "I paid Rs 500 to the plumber for you",
            senderKeywords = emptyList()
        )
        assertNotNull(parsed)
        assertEquals(500.0, parsed!!.amount, 0.001)
    }

    @Test
    fun extra_keyword_enables_detection() {
        val body = "Rs.120.00 autopaid for Spotify. Ref 400177778888"
        assertNull(SmsParser.parse("VM-HDFCBK", body))
        val parsed = SmsParser.parse(
            sender = "VM-HDFCBK",
            body = body,
            extraKeywords = listOf("autopaid")
        )
        assertNotNull(parsed)
        assertEquals(120.0, parsed!!.amount, 0.001)
        assertEquals("400177778888", parsed.upiRef)
    }

    // ---- Dedupe ---------------------------------------------------------------------------

    @Test
    fun dedupe_key_uses_upi_reference_when_present() {
        val first = SmsParser.dedupeKey(500.0, 1_700_000_000_000L, "VM-HDFCBK", "400112345678")
        val second = SmsParser.dedupeKey(500.0, 1_700_000_999_000L, "JD-HDFCBK", "400112345678")
        assertEquals(first, second)
        assertEquals("ref:400112345678", first)
    }

    @Test
    fun dedupe_key_falls_back_to_hash_of_amount_timestamp_sender() {
        val a = SmsParser.dedupeKey(500.0, 1_700_000_000_000L, "VM-HDFCBK", null)
        val same = SmsParser.dedupeKey(500.0, 1_700_000_000_000L, "vm-hdfcbk", null)
        val otherTime = SmsParser.dedupeKey(500.0, 1_700_000_060_000L, "VM-HDFCBK", null)
        val otherAmount = SmsParser.dedupeKey(501.0, 1_700_000_000_000L, "VM-HDFCBK", null)
        assertTrue(a.startsWith("hash:"))
        assertEquals(a, same)
        assertNotEquals(a, otherTime)
        assertNotEquals(a, otherAmount)
    }
}
