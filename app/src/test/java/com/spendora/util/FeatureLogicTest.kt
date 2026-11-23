package com.spendora.util

import com.spendora.data.Expense
import com.spendora.data.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureLogicTest {

    private fun expense(
        amount: Double,
        label: String,
        timestamp: Long,
        status: String = Expense.STATUS_SAVED,
        manual: Boolean = true
    ) = Expense(
        amount = amount,
        label = label,
        timestamp = timestamp,
        status = status,
        isManual = manual,
        dedupeKey = "test:$label:$timestamp"
    )

    @Test
    fun categories_have_stable_ids_and_colors() {
        assertEquals(7, ExpenseCategory.all.size)
        assertEquals("food", ExpenseCategory.FOOD.id)
        assertEquals(ExpenseCategory.TRAVEL, ExpenseCategory.from("travel"))
        assertEquals(ExpenseCategory.OTHER, ExpenseCategory.from(null))
        assertTrue(ExpenseCategory.all.all { it.color != 0L })
    }

    @Test
    fun insights_summarise_only_saved_expenses() {
        val day = 86_400_000L
        val expenses = listOf(
            expense(100.0, "Cafe", day),
            expense(200.0, "Cafe", day * 8),
            expense(50.0, "Metro", day * 2),
            expense(999.0, "Skipped", day * 3, status = Expense.STATUS_PENDING)
        )
        val insights = InsightsEngine.build(expenses)
        assertEquals(350.0, insights.total, 0.001)
        assertEquals(350.0 / 3, insights.average, 0.001)
        assertEquals("Cafe", insights.merchants.first().name)
        assertEquals(2, insights.merchants.first().count)
        assertFalse(insights.merchants.any { it.name == "Skipped" })
        assertTrue(insights.months.isNotEmpty())
        assertTrue(insights.weekdays.isNotEmpty())
    }

    @Test
    fun recurring_detector_groups_repeat_payments() {
        val day = 86_400_000L
        val expenses = listOf(
            expense(199.0, "Spotify", day),
            expense(199.0, "spotify", day * 31),
            expense(199.0, "SPOTIFY", day * 61),
            expense(75.0, "Cab", day * 2)
        )
        val groups = RecurringDetector.detect(expenses)
        assertEquals(1, groups.size)
        assertEquals("spotify", groups.first().label)
        assertEquals(3, groups.first().count)
        assertEquals(199.0, groups.first().averageAmount, 0.001)
        assertEquals(30, groups.first().cadenceDays)
    }

    @Test
    fun sms_rule_tester_explains_rejections_and_accepts_debits() {
        val rejected = SmsRuleTester.test("VM-HDFCBK", "Your OTP is 123456", listOf("HDFC"), emptyList())
        assertFalse(rejected.accepted)
        assertTrue(rejected.reason.contains("OTP", ignoreCase = true))

        val accepted = SmsRuleTester.test(
            "VM-HDFCBK",
            "Sent Rs.499.00 From HDFC Bank To SWIGGY On 23/11/26 Ref 400112345678",
            listOf("HDFC"),
            emptyList()
        )
        assertTrue(accepted.accepted)
        assertEquals(499.0, accepted.amount ?: 0.0, 0.001)
        assertEquals("400112345678", accepted.upiRef)

        val wrongSender = SmsRuleTester.test("UNKNOWN", "Sent Rs.10 to X Ref 123", listOf("HDFC"), emptyList())
        assertFalse(wrongSender.accepted)
        assertTrue(wrongSender.reason.contains("Sender", ignoreCase = true))
    }
}
