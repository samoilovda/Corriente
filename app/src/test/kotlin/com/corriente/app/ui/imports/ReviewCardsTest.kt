package com.corriente.app.ui.imports

import com.corriente.data.imports.MonefyCsvParser
import com.corriente.data.imports.MonefyImportPlanner
import com.corriente.data.imports.ReviewDecision
import com.corriente.data.imports.ReviewReason
import com.corriente.data.imports.applyReviewDecisions
import com.corriente.data.imports.ref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** T3.3: карточки NEEDS_REVIEW для экрана и пересчёт сводки под выбранные решения. */
class ReviewCardsTest {

    private val plan by lazy {
        val csv = javaClass.classLoader!!.getResourceAsStream("monefy_sample.csv")!!
            .readBytes().toString(Charsets.UTF_8)
        MonefyImportPlanner.plan(MonefyCsvParser.parse(csv))
    }

    @Test
    fun `every review becomes a card, transfer amounts attached`() {
        val cards = reviewCards(plan, emptyMap())
        assertEquals(plan.reviews.size, cards.size)

        val excess = cards.single { it.reason == ReviewReason.EXCESS_PRECISION }
        assertEquals(869_500L, excess.fromAmountMinor)
        assertEquals(10_000L, excess.toAmountMinor)
        assertEquals(2, excess.fromMinorUnits) // RUB
        assertNull(excess.decision)
    }

    @Test
    fun `a chosen decision is echoed back on the card`() {
        val ref = plan.reviews.single { it.reason == ReviewReason.ANOMALOUS_CURRENCY }.ref()
        val cards = reviewCards(plan, mapOf(ref to ReviewDecision.SameCurrency))
        assertEquals(ReviewDecision.SameCurrency, cards.single { it.reason == ReviewReason.ANOMALOUS_CURRENCY }.decision)
    }

    @Test
    fun `summary reflects decisions - unpairing an ambiguous pair drops two transfers`() {
        val ref = plan.reviews.single { it.reason == ReviewReason.AMBIGUOUS_PAIRING }.ref()
        val before = plan.toImportSummary()
        val after = plan.applyReviewDecisions(mapOf(ref to ReviewDecision.KeepSeparate)).toImportSummary()

        assertEquals(before.transfers - 2, after.transfers)
        assertEquals(before.unpairedHalves + 4, after.unpairedHalves)
        assertTrue(after.reviews.none { it.reason == ReviewReason.AMBIGUOUS_PAIRING })
    }
}
