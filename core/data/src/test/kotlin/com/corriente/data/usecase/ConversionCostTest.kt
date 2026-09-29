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
        // |85-90|/90 · 8500.00 + |95-90|/90 · 9500.00 = 472.2222… + 527.7777… = 1000.00 GBP — сумма точная, округляется
        // ровно один раз в конце (I-4), а не по сделкам с потерей копеек.
        assertEquals(100_000L, result.cost.amount.raw)
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
        fun bd(vararg v: Long) = v.map { java.math.BigDecimal(it) }
        assertEquals(0, java.math.BigDecimal(15).compareTo(median(bd(10, 20))))
        assertEquals(0, java.math.BigDecimal(20).compareTo(median(bd(10, 20, 30))))
        assertEquals(0, java.math.BigDecimal(25).compareTo(median(bd(40, 10, 30, 20))))
    }

    // A2.14: курс порядка 1e-7 (IRR → USD и подобные) раньше давал rateMicros = 0 и деление на ноль в combine → краш экрана.
    @Test
    fun `tiny rates do not divide by zero and are estimated from the exact rate`() {
        val irr = CurrencyCode("IRR")
        fun tiny(id: String, month: Int, irrMinor: Long, usdMinor: Long) = FxDeal(
            txnId = id, date = LocalDate.of(2026, month, 1),
            from = Money(Minor(irrMinor), irr), to = Money(Minor(usdMinor), usd),
            rateLabel = "", rateMicros = 0L, // округление до 1e-6 обнуляет курс
            rate = java.math.BigDecimal(usdMinor).divide(java.math.BigDecimal(irrMinor), java.math.MathContext.DECIMAL64)
                .movePointLeft(0),
        )
        val deals = listOf(
            tiny("a", 1, 10_000_000_000L, 100L),
            tiny("b", 2, 10_000_000_000L, 101L),
            tiny("c", 3, 10_000_000_000L, 110L),
        )

        val result = conversionCost(deals).single()

        assertEquals(3, result.dealCount)
        assertTrue(result.cost != null && result.cost!!.amount.raw > 0)
    }

    // A2.14: крупные суммы не переполняют Math.multiplyExact — либо число, либо «недостаточно данных», не краш.
    @Test
    fun `huge amounts do not overflow`() {
        val big = 900_000_000_000_000_000L
        val deals = listOf(
            eurToGbp("a", LocalDate.of(2026, 1, 1), 1_000_00, 90_000_000).copy(to = Money(Minor(big), gbp)),
            eurToGbp("b", LocalDate.of(2026, 2, 1), 1_000_00, 80_000_000).copy(to = Money(Minor(big), gbp)),
            eurToGbp("c", LocalDate.of(2026, 3, 1), 1_000_00, 100_000_000).copy(to = Money(Minor(big), gbp)),
        )
        conversionCost(deals) // не бросает
    }
}
