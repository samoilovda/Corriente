package com.corriente.app.ui.accounts

import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test

/** UI-2.5 / I-8: итог на карточке валюты — ровно итог её группы, валюты не смешиваются. */
class CurrencyCardsTest {

    private val rub = Currency(CurrencyCode("RUB"), minorUnits = 2, displayScale = 2, symbol = "₽")
    private val usd = Currency(CurrencyCode("USD"), minorUnits = 2, displayScale = 2, symbol = "$")

    @Test
    fun `карточка на каждую группу, итог совпадает с итогом группы`() {
        val rubTotal = Money(Minor(-2_811_175), rub.code)
        val usdTotal = Money(Minor(6_674_650), usd.code)
        val groups = listOf(
            CurrencyBalanceGroup(rub, rows = emptyList(), total = rubTotal),
            CurrencyBalanceGroup(usd, rows = emptyList(), total = usdTotal),
        )

        val cards = currencyCards(groups)

        assertEquals(listOf("RUB", "USD"), cards.map { it.code })
        assertEquals(listOf<Money?>(rubTotal, usdTotal), cards.map { it.total })
        assertEquals(listOf("₽", "$"), cards.map { it.symbol })
    }

    @Test
    fun `нет групп — нет карточек`() {
        assertEquals(emptyList<CurrencyCard>(), currencyCards(emptyList()))
    }
}
