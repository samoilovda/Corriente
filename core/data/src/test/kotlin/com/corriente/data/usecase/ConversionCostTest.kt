package com.corriente.data.usecase

import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** R3.4: развитие T5.4 — цена отклонения курса сделки от медианы той же пары. */
class ConversionCostTest {

    private val eur = CurrencyCode("EUR") // "EUR" < "GBP" лексикографически: EUR — база пары.
    private val gbp = CurrencyCode("GBP")
    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")

    private fun deal(id: String, date: LocalDate, from: Money, to: Money, rateMicros: Long) = FxDeal(
        txnId = id,
        date = date,
        from = from,
        to = to,
        rateLabel = "",
        rateMicros = rateMicros,
    )

    /** EUR -> GBP: направление совпадает с базой пары — курс участвует в медиане как есть. */
    private fun eurToGbp(id: String, date: LocalDate, eurAmount: Long, rateMicros: Long) = deal(
        id, date,
        from = Money(Minor(eurAmount), eur),
        to = Money(Minor(Math.multiplyExact(eurAmount, rateMicros) / 1_000_000L), gbp),
        rateMicros = rateMicros,
    )

    private fun usdToRub(id: String, date: LocalDate, usdAmount: Long, rateMicros: Long) = deal(
        id, date,
        from = Money(Minor(usdAmount), usd),
        to = Money(Minor(Math.multiplyExact(usdAmount, rateMicros) / 1_000_000L), rub),
        rateMicros = rateMicros,
    )

    @Test
    fun `one deal is insufficient data`() {
        val deals = listOf(eurToGbp("t1", LocalDate.of(2026, 1, 1), 100_00, 90_000_000))
        val result = conversionCost(deals).single()
        assertEquals(1, result.dealCount)
        assertNull(result.cost)
    }

    @Test
    fun `two deals are insufficient data`() {
        val deals = listOf(
            eurToGbp("t1", LocalDate.of(2026, 1, 1), 100_00, 90_000_000),
            eurToGbp("t2", LocalDate.of(2026, 2, 1), 100_00, 91_000_000),
        )
        val result = conversionCost(deals).single()
        assertEquals(2, result.dealCount)
        assertNull(result.cost)
    }

    @Test
    fun `three deals at the same rate cost nothing (no deviation from the median)`() {
        val deals = listOf(
            eurToGbp("t1", LocalDate.of(2026, 1, 1), 100_00, 90_000_000),
            eurToGbp("t2", LocalDate.of(2026, 2, 1), 200_00, 90_000_000),
            eurToGbp("t3", LocalDate.of(2026, 3, 1), 300_00, 90_000_000),
        )
        val result = conversionCost(deals).single()
        assertEquals(3, result.dealCount)
        assertEquals(Money(Minor(0), gbp), result.cost)
        assertEquals(eur, result.baseCurrency)
        assertEquals(gbp, result.quoteCurrency)
    }

    @Test
    fun `deviation from the median produces a non-zero cost in the quote currency`() {
        val deals = listOf(
            eurToGbp("t1", LocalDate.of(2026, 1, 1), 100_00, 85_000_000), // ниже медианы
            eurToGbp("t2", LocalDate.of(2026, 2, 1), 100_00, 90_000_000), // медиана
            eurToGbp("t3", LocalDate.of(2026, 3, 1), 100_00, 95_000_000), // выше медианы
        )
        val result = conversionCost(deals).single()
        assertEquals(3, result.dealCount)
        assertEquals(gbp, result.cost!!.currency)
        assertTrue(result.cost.amount.raw > 0)
        // |85-90|/90 и |95-90|/90 от суммы в GBP каждой сделки (100 EUR по своему курсу), сложенные по модулю.
        val gbp1 = Math.multiplyExact(100_00L, 85_000_000L) / 1_000_000L
        val gbp3 = Math.multiplyExact(100_00L, 95_000_000L) / 1_000_000L
        val cost1 = kotlin.math.abs(Math.multiplyExact(-5_000_000L, gbp1) / 90_000_000L)
        val cost3 = kotlin.math.abs(Math.multiplyExact(5_000_000L, gbp3) / 90_000_000L)
        assertEquals(cost1 + cost3, result.cost.amount.raw)
    }

    // Ключевое требование R3.4: A→B и B→A — одна и та же пара, а не две разные.
    @Test
    fun `reverse-direction deals are folded into the same pair and count towards the threshold`() {
        val rubToUsd = deal(
            "t3", LocalDate.of(2026, 3, 1),
            from = Money(Minor(9_000_00), rub), to = Money(Minor(100_00), usd),
            // rateMicros = to/from = 100/9000 ≈ 0.011111 => micros ≈ 11_111
            rateMicros = 11_111L,
        )
        val deals = listOf(
            usdToRub("t1", LocalDate.of(2026, 1, 1), 100_00, 90_000_000),
            usdToRub("t2", LocalDate.of(2026, 2, 1), 100_00, 90_000_000),
            rubToUsd,
        )
        val results = conversionCost(deals)
        assertEquals(1, results.size)
        val result = results.single()
        assertEquals(3, result.dealCount)
        // База/квота — устойчивый лексикографический порядок валют пары, не зависит от того,
        // какое направление было первым по времени.
        assertEquals(rub, result.baseCurrency)
        assertEquals(usd, result.quoteCurrency)
        assertTrue(result.cost != null)
    }

    @Test
    fun `two separate pairs stay separate`() {
        val deals = listOf(
            eurToGbp("t1", LocalDate.of(2026, 1, 1), 100_00, 90_000_000),
            eurToGbp("t2", LocalDate.of(2026, 2, 1), 100_00, 90_000_000),
            eurToGbp("t3", LocalDate.of(2026, 3, 1), 100_00, 90_000_000),
            usdToRub("t4", LocalDate.of(2026, 1, 1), 100_00, 85_000_000),
        )
        val results = conversionCost(deals)
        assertEquals(2, results.size)
        assertEquals(setOf(eur to gbp, rub to usd), results.map { it.baseCurrency to it.quoteCurrency }.toSet())
    }

    @Test
    fun `median of an even-sized list averages the two middle values`() {
        assertEquals(15L, medianMicros(listOf(10L, 20L)))
        assertEquals(20L, medianMicros(listOf(10L, 20L, 30L)))
        assertEquals(25L, medianMicros(listOf(40L, 10L, 30L, 20L)))
    }
}
