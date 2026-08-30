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

/**
 * Логика этого файла заранее прогнана вне Android/Room в throwaway JVM-гарнитуре (см. итоговое
 * сообщение сессии) — 5/5 зелёных на тех же сценариях. Здесь — тот же тест на реальных
 * доменных типах модуля, для прогона в Android Studio (README "Известное ограничение окружения").
 */
class AccountBalanceUseCaseTest {

    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")

    private fun account(id: String, currency: CurrencyCode, opening: Long, includeInTotal: Boolean = true) = Account(
        id = id, name = id, currency = currency, kind = AccountKind.CASH,
        openingBalance = Money(Minor(opening), currency), color = 0, icon = null,
        displayOrder = 0, isArchived = false, includeInTotal = includeInTotal,
    )

    @Test
    fun `balance is opening plus income minus expense, transfers excluded from other accounts`() {
        val cash = account("cash", rub, opening = 10_000_00)
        val other = account("other", rub, opening = 0)
        val txns = listOf(
            Txn.Income("i1", LocalDate.of(2026, 1, 1), 0, 0, "cash", Money(Minor(5_000_00), rub), null),
            Txn.Expense("e1", LocalDate.of(2026, 1, 2), 0, 0, "cash", Money(Minor(1_250_75), rub), null),
            Txn.Expense("e2", LocalDate.of(2026, 1, 3), 0, 0, "other", Money(Minor(999_00), rub), null),
        )
        assertEquals(Money(Minor(13_749_25), rub), accountBalance(cash, txns))
    }

    @Test
    fun `cross-currency transfer debits one account and credits the other in its own currency`() {
        // Пара строк из testdata/monefy_sample.csv (05/03/2021): Cash -8695 RUB -> Card $ +100.001 USD.
        // Здесь - парсящаяся сумма (2 знака): "100.001" при minorUnits=2 отвергается самим
        // MonefyAmountParser (docs/MONEFY_IMPORT.md §4), до Txn такая строка не доходит вовсе.
        val cashRub = account("cash", rub, opening = 100_000_00)
        val cardUsd = account("card_usd", usd, opening = 0)
        val transfer = Txn.Transfer(
            id = "t1", date = LocalDate.of(2021, 3, 5), createdAt = 0, updatedAt = 0,
            fromAccountId = "cash", fromAmount = Money(Minor(8_695_00), rub),
            toAccountId = "card_usd", toAmount = Money(Minor(100_00), usd),
        )
        assertEquals(Money(Minor(91_305_00), rub), accountBalance(cashRub, listOf(transfer)))
        assertEquals(Money(Minor(100_00), usd), accountBalance(cardUsd, listOf(transfer)))
    }

    @Test
    fun `transfer never leaks into a third, unrelated account`() {
        val bystander = account("bystander", rub, opening = 500_00)
        val transfer = Txn.Transfer(
            "t1", LocalDate.of(2026, 1, 1), 0, 0,
            "cash", Money(Minor(100_00), rub), "savings", Money(Minor(100_00), rub),
        )
        assertEquals(Money(Minor(500_00), rub), accountBalance(bystander, listOf(transfer)))
    }

    @Test
    fun `totalsByCurrency groups by currency and skips accounts excluded from the total`() {
        val cash = AccountBalance(account("cash", rub, 0), Money(Minor(10_000_00), rub))
        val savings = AccountBalance(account("savings", rub, 0), Money(Minor(5_000_00), rub))
        val cardUsd = AccountBalance(account("card_usd", usd, 0), Money(Minor(900_00), usd))
        val hiddenDebt = AccountBalance(
            account("debt", rub, 0, includeInTotal = false),
            Money(Minor(-50_000_00), rub),
        )

        val totals = totalsByCurrency(listOf(cash, savings, cardUsd, hiddenDebt))

        assertEquals(Money(Minor(15_000_00), rub), totals.getValue(rub))
        assertEquals(Money(Minor(900_00), usd), totals.getValue(usd))
        assertEquals(2, totals.size)
    }

    @Test
    fun `totalsByCurrency never mixes different currencies into one number - ADR-012`() {
        val cash = AccountBalance(account("cash", rub, 0), Money(Minor(1), rub))
        val cardUsd = AccountBalance(account("card_usd", usd, 0), Money(Minor(1), usd))
        val totals = totalsByCurrency(listOf(cash, cardUsd))
        assertEquals(setOf(rub, usd), totals.keys)
    }
}
