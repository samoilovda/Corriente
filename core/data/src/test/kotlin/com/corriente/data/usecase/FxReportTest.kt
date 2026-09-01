package com.corriente.data.usecase

import com.corriente.data.model.Txn
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** T5.4: фактические курсы обмена выводятся только из собственных межвалютных переводов. */
class FxReportTest {

    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")
    private val meta = mapOf(rub to Currency(rub, 2, 2, "₽"), usd to Currency(usd, 2, 2, "$"))

    private fun transfer(id: String, date: LocalDate, from: Money, to: Money) =
        Txn.Transfer(id, date, 0, 0, "a", from, "b", to)

    @Test
    fun `only cross-currency transfers produce deals, grouped by pair and sorted by date`() {
        val txns = listOf(
            transfer("t1", LocalDate.of(2026, 1, 5), Money(Minor(100_00), usd), Money(Minor(8_695_00), rub)),
            transfer("t2", LocalDate.of(2026, 3, 1), Money(Minor(50_00), usd), Money(Minor(4_500_00), rub)),
            // одновалютный перевод — не курс
            transfer("t3", LocalDate.of(2026, 2, 1), Money(Minor(1_000_00), rub), Money(Minor(1_000_00), rub)),
            // обычный расход
            Txn.Expense("e1", LocalDate.of(2026, 2, 2), 0, 0, "a", Money(Minor(500_00), rub), null),
        )

        val pairs = fxDeals(txns, meta)
        assertEquals(1, pairs.size)
        val pair = pairs.single()
        assertEquals(usd to rub, pair.from to pair.to)
        assertEquals(listOf("t1", "t2"), pair.deals.map { it.txnId })
        assertEquals("1 USD = 86.95 RUB", pair.deals.first().rateLabel)
        assertEquals(86_950_000L, pair.deals.first().rateMicros) // 86.95 * 1e6
        assertEquals(90_000_000L, pair.deals[1].rateMicros)      // 4500/50 = 90
    }

    @Test
    fun `zero source amount yields no deal`() {
        val txns = listOf(
            transfer("z", LocalDate.of(2026, 1, 1), Money(Minor(0), usd), Money(Minor(100_00), rub)),
        )
        assertTrue(fxDeals(txns, meta).isEmpty())
    }
}
