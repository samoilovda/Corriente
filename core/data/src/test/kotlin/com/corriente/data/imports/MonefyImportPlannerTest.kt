package com.corriente.data.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * T3.2: классификация и склейка на `testdata/monefy_sample.csv` строго по `expected.md`
 * (вариант А). Проверяет все случаи NEEDS_REVIEW.
 */
class MonefyImportPlannerTest {

    private val plan by lazy {
        val csv = listOf(File("../../testdata/monefy_sample.csv"), File("testdata/monefy_sample.csv"))
            .first { it.exists() }.readText(Charsets.UTF_8)
        MonefyImportPlanner.plan(MonefyCsvParser.parse(csv))
    }

    @Test
    fun `accounts are created from column 1 with their currency and initial balance`() {
        val byName = plan.accounts.associateBy { it.name }
        assertEquals(setOf("Cash", "Card \$", "Savings", "Wallet"), byName.keys)
        assertEquals("RUB", byName.getValue("Cash").currency.code)
        assertEquals(1_000_000L, byName.getValue("Cash").openingBalanceMinor) // 10 000 RUB
        assertEquals("USD", byName.getValue("Card \$").currency.code)
        assertEquals(0L, byName.getValue("Card \$").openingBalanceMinor)
        assertEquals(8_000L, byName.getValue("Wallet").openingBalanceMinor)   // 80 RUB
    }

    @Test
    fun `pseudo-categories do not become categories`() {
        assertEquals(listOf("Food", "Eating out", "Salary", "Дом, ремонт", "Travel"), plan.categories)
    }

    @Test
    fun `six plain operations plus one unpaired half`() {
        val real = plan.plainTxns.filterNot { it.unpairedHalf }
        assertEquals(6, real.size)
        assertEquals(2, real.count { it.category == "Food" })
        assertEquals(125_075L, real.single { it.category == "Eating out" }.amountMinor)
        assertEquals(MonefyTxnKind.INCOME, real.single { it.category == "Salary" }.kind)

        val unpaired = plan.plainTxns.single { it.unpairedHalf }
        assertEquals(UNPAIRED_TRANSFER_CATEGORY, unpaired.category)
        assertEquals(LocalDate.of(2021, 3, 10), unpaired.date)
        assertEquals(300_000L, unpaired.amountMinor) // 3 000 RUB
        assertEquals(MonefyTxnKind.EXPENSE, unpaired.kind)
    }

    @Test
    fun `five transfer pairs, one clean and four flagged`() {
        assertEquals(5, plan.transfers.size)
        val clean = plan.transfers.filter { it.review == null }
        assertEquals(1, clean.size)
        assertEquals(LocalDate.of(2021, 3, 7), clean.single().date) // Cash → Savings, одна валюта
    }

    @Test
    fun `NEEDS_REVIEW - ambiguous pairing on the same day`() {
        val ambiguous = plan.transfers.filter { it.review == ReviewReason.AMBIGUOUS_PAIRING }
        assertEquals(2, ambiguous.size)
        assertTrue(ambiguous.all { it.date == LocalDate.of(2021, 3, 8) })
        assertEquals(1, plan.reviews.count { it.reason == ReviewReason.AMBIGUOUS_PAIRING })
    }

    @Test
    fun `NEEDS_REVIEW - anomalous currency, implied rate 1_0`() {
        val anomaly = plan.transfers.single { it.review == ReviewReason.ANOMALOUS_CURRENCY }
        assertEquals(LocalDate.of(2021, 3, 9), anomaly.date)
        assertTrue(anomaly.fromCurrency != anomaly.toCurrency)
        assertEquals(anomaly.fromAmountMinor, anomaly.toAmountMinor) // 66 667 == 66 667
        assertEquals(1, plan.reviews.count { it.reason == ReviewReason.ANOMALOUS_CURRENCY })
    }

    @Test
    fun `NEEDS_REVIEW - excess precision half is kept as a transfer but flagged (variant A)`() {
        val excess = plan.transfers.single { it.review == ReviewReason.EXCESS_PRECISION }
        assertEquals(LocalDate.of(2021, 3, 5), excess.date)
        assertEquals(869_500L, excess.fromAmountMinor)
        assertEquals(10_000L, excess.toAmountMinor) // 100.001 → 100.00
        assertEquals(1, plan.reviews.count { it.reason == ReviewReason.EXCESS_PRECISION })
    }

    @Test
    fun `no parse errors on the sample`() {
        assertEquals(emptyList<MonefyRowError>(), plan.errors)
    }
}
