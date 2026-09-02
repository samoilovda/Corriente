package com.corriente.data.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T3.3 / BACKLOG: ручное разрешение NEEDS_REVIEW на экране dry-run. Проверяет каждую ветку
 * [applyReviewDecisions] на `testdata/monefy_sample.csv` (там есть все три причины переводов).
 */
class MonefyReviewResolutionTest {

    private val plan by lazy {
        val csv = javaClass.classLoader!!.getResourceAsStream("monefy_sample.csv")!!
            .readBytes().toString(Charsets.UTF_8)
        MonefyImportPlanner.plan(MonefyCsvParser.parse(csv))
    }

    private fun refFor(reason: ReviewReason) = plan.reviews.single { it.reason == reason }.ref()

    @Test
    fun `no decisions leaves the plan untouched`() {
        assertEquals(plan, plan.applyReviewDecisions(emptyMap()))
    }

    @Test
    fun `Accept clears only that review and keeps the transfer`() {
        val ref = refFor(ReviewReason.EXCESS_PRECISION)
        val out = plan.applyReviewDecisions(mapOf(ref to ReviewDecision.Accept))

        assertTrue(out.reviews.none { it.reason == ReviewReason.EXCESS_PRECISION })
        assertEquals(2, out.reviews.size) // ambiguous + anomalous остались
        assertEquals(plan.transfers.size, out.transfers.size)
        assertNull(out.transfers.single { it.date == java.time.LocalDate.of(2021, 3, 5) }.review)
    }

    @Test
    fun `KeepSeparate unpairs both ambiguous transfers into four halves`() {
        val ref = refFor(ReviewReason.AMBIGUOUS_PAIRING)
        val out = plan.applyReviewDecisions(mapOf(ref to ReviewDecision.KeepSeparate))

        assertEquals(plan.transfers.size - 2, out.transfers.size)
        assertTrue(out.transfers.none { it.review == ReviewReason.AMBIGUOUS_PAIRING })
        val added = out.plainTxns.count { it.unpairedHalf } - plan.plainTxns.count { it.unpairedHalf }
        assertEquals(4, added)
        assertTrue(out.plainTxns.filter { it.unpairedHalf }.all { it.category == UNPAIRED_TRANSFER_CATEGORY })
        assertTrue(out.reviews.none { it.reason == ReviewReason.AMBIGUOUS_PAIRING })
    }

    @Test
    fun `SameCurrency collapses the anomalous transfer to one currency`() {
        val ref = refFor(ReviewReason.ANOMALOUS_CURRENCY)
        val out = plan.applyReviewDecisions(mapOf(ref to ReviewDecision.SameCurrency))

        val tx = out.transfers.single { it.date == java.time.LocalDate.of(2021, 3, 9) }
        assertEquals(tx.fromCurrency, tx.toCurrency)
        assertEquals(tx.fromAmountMinor, tx.toAmountMinor)
        assertNull(tx.review)
        assertTrue(out.reviews.none { it.reason == ReviewReason.ANOMALOUS_CURRENCY })
    }

    @Test
    fun `ExactAmounts overrides the rounded transfer amounts`() {
        val ref = refFor(ReviewReason.EXCESS_PRECISION)
        val out = plan.applyReviewDecisions(
            mapOf(ref to ReviewDecision.ExactAmounts(fromMinor = 869_500L, toMinor = 10_001L)),
        )

        val tx = out.transfers.single { it.date == java.time.LocalDate.of(2021, 3, 5) }
        assertEquals(869_500L, tx.fromAmountMinor)
        assertEquals(10_001L, tx.toAmountMinor)
        assertNull(tx.review)
    }

    // F0.5 — «отдельный счёт» переименовывает плановый счёт и все ссылки на него.
    @Test
    fun `SeparateAccount renames the account and its transactions`() {
        val csv = javaClass.classLoader!!.getResourceAsStream("monefy_sample.csv")!!
            .readBytes().toString(Charsets.UTF_8)
        val p = MonefyImportPlanner.plan(
            MonefyCsvParser.parse(csv),
            existingAccounts = listOf("Cash" to com.corriente.money.CurrencyCode("USD")),
        )
        val ref = p.reviews.single { it.reason == ReviewReason.EXISTING_ACCOUNT_CURRENCY_MISMATCH }.ref()
        val out = p.applyReviewDecisions(mapOf(ref to ReviewDecision.SeparateAccount))

        assertTrue(out.accounts.none { it.name == "Cash" })
        assertTrue(out.accounts.any { it.name == "Cash (RUB)" })
        assertTrue(out.plainTxns.none { it.account == "Cash" })
        assertTrue(out.transfers.none { it.fromAccount == "Cash" || it.toAccount == "Cash" })
        assertTrue(out.reviews.none { it.reason == ReviewReason.EXISTING_ACCOUNT_CURRENCY_MISMATCH })
    }

    @Test
    fun `decisions do not touch unrelated transfers`() {
        val ref = refFor(ReviewReason.EXCESS_PRECISION)
        val out = plan.applyReviewDecisions(mapOf(ref to ReviewDecision.Accept))
        val clean = out.transfers.single { it.review == null && it.date == java.time.LocalDate.of(2021, 3, 7) }
        assertEquals(plan.transfers.single { it.date == java.time.LocalDate.of(2021, 3, 7) }, clean)
    }
}
