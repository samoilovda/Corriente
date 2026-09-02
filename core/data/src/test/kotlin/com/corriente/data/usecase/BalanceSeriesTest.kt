package com.corriente.data.usecase

import com.corriente.data.db.entity.AccountKind
import com.corriente.data.model.Account
import com.corriente.data.model.Txn
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** R3.2: остаток счёта день за днём — накопительный итог поверх [accountBalance]. */
class BalanceSeriesTest {

    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")

    private fun account(id: String, opening: Long, currency: CurrencyCode = rub) = Account(
        id = id,
        name = id,
        currency = currency,
        kind = AccountKind.CASH,
        openingBalance = Money(Minor(opening), currency),
        color = 0,
        icon = null,
        displayOrder = 0,
        isArchived = false,
        includeInTotal = true,
    )

    private fun expense(id: String, date: LocalDate, accountId: String, amount: Long, currency: CurrencyCode = rub) =
        Txn.Expense(id, date, 0, 0, accountId, Money(Minor(amount), currency), null)

    private fun income(id: String, date: LocalDate, accountId: String, amount: Long, currency: CurrencyCode = rub) =
        Txn.Income(id, date, 0, 0, accountId, Money(Minor(amount), currency), null)

    @Test
    fun `opening balance carries into the first day of the range even with no transactions`() {
        val acc = account("a", opening = 10_000_00)
        val series = balanceSeries(acc, emptyList(), LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 3))
        assertEquals(
            listOf(
                LocalDate.of(2026, 3, 1) to 10_000_00L,
                LocalDate.of(2026, 3, 2) to 10_000_00L,
                LocalDate.of(2026, 3, 3) to 10_000_00L,
            ),
            series.map { it.date to it.balance.amount.raw },
        )
    }

    // Критерий приёмки: начальный остаток периода учитывает всё, что было ДО него, не только
    // openingBalance счёта, — история до диапазона тоже часть «начального остатка».
    @Test
    fun `history before the range is folded into the balance at the start of the range`() {
        val acc = account("a", opening = 1_000_00)
        val before = income("i0", LocalDate.of(2026, 2, 15), "a", 500_00)
        val series = balanceSeries(acc, listOf(before), LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 1))
        assertEquals(1_500_00L, series.single().balance.amount.raw)
    }

    @Test
    fun `each day's transactions move the running balance by day's end`() {
        val acc = account("a", opening = 0)
        val txns = listOf(
            income("i1", LocalDate.of(2026, 3, 1), "a", 1_000_00),
            expense("e1", LocalDate.of(2026, 3, 2), "a", 300_00),
            expense("e2", LocalDate.of(2026, 3, 2), "a", 200_00), // two on the same day
            // day 3 has no transactions — balance carries forward flat
            income("i2", LocalDate.of(2026, 3, 4), "a", 50_00),
        )
        val series = balanceSeries(acc, txns, LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 4))
        assertEquals(
            listOf(1_000_00L, 500_00L, 500_00L, 550_00L),
            series.map { it.balance.amount.raw },
        )
    }

    // Переводы отражены с правильным знаком: минус у счёта-источника, плюс у счёта-приёмника.
    @Test
    fun `transfers move both accounts with opposite signs`() {
        val cash = account("cash", opening = 1_000_00)
        val card = account("card", opening = 500_00)
        val transfer = Txn.Transfer(
            "t1", LocalDate.of(2026, 3, 1), 0, 0, "cash", Money(Minor(400_00), rub), "card", Money(Minor(400_00), rub),
        )
        val range = LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 1)
        assertEquals(600_00L, balanceSeries(cash, listOf(transfer), range).single().balance.amount.raw)
        assertEquals(900_00L, balanceSeries(card, listOf(transfer), range).single().balance.amount.raw)
    }

    // Перевод в чужой валюте не в счёте вообще не участвует (I-15: валюта операции = валюта счёта).
    @Test
    fun `transactions on other accounts and other currencies do not affect this account's series`() {
        val acc = account("a", opening = 1_000_00)
        val txns = listOf(
            income("other", LocalDate.of(2026, 3, 1), "b", 999_00),
            expense("otherCur", LocalDate.of(2026, 3, 1), "a-usd", 1_00, usd),
        )
        val series = balanceSeries(acc, txns, LocalDate.of(2026, 3, 1)..LocalDate.of(2026, 3, 1))
        assertEquals(1_000_00L, series.single().balance.amount.raw)
    }

    // "Счёт с сотней операций" — критерий приёмки: строится корректно на длинной истории.
    @Test
    fun `a hundred transactions produce a correct daily series over the same range`() {
        val acc = account("a", opening = 0)
        val start = LocalDate.of(2026, 1, 1)
        val txns = (0 until 100).map { i ->
            val day = start.plusDays((i % 30).toLong())
            if (i % 2 == 0) income("i$i", day, "a", 100_00) else expense("e$i", day, "a", 40_00)
        }
        val range = start..start.plusDays(29)
        val series = balanceSeries(acc, txns, range)

        assertEquals(30, series.size)
        // Итоговый остаток на конец диапазона обязан совпасть с accountBalance по всей истории.
        assertEquals(accountBalance(acc, txns).amount.raw, series.last().balance.amount.raw)
        // 50 доходов по 100_00 (=10000 минорных единиц) и 50 расходов по 40_00 (=4000):
        // 50*10000 - 50*4000 = 300000.
        assertEquals(300_000L, series.last().balance.amount.raw)
    }
}
