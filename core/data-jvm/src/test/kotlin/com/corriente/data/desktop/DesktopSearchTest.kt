package com.corriente.data.desktop

import com.corriente.data.db.entity.AccountKind
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** W11: поиск на десктопе (SQLite без ICU) находит счета и категории с заглавной кириллицей при любом регистре запроса. */
class DesktopSearchTest {

    private val rub = CurrencyCode("RUB")

    @Test
    fun cyrillicAccountAndCategoryNamesAreFoundCaseInsensitively() = runBlocking {
        val db = DesktopDatabase.inMemory()
        val txns = TxnRepository(db.txnDao(), db.accountDao(), db.categoryDao())
        val card = AccountRepository(db.accountDao()).create("Тинькофф", rub, AccountKind.CARD, Money(Minor(0), rub), 0)
        val cash = AccountRepository(db.accountDao()).create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0)
        val byCard = txns.addExpense(card.id, Money(Minor(100), rub), null, LocalDate.of(2026, 1, 1))
        val groceries = txns.addExpense(cash.id, Money(Minor(200), rub), "seed-exp-groceries", LocalDate.of(2026, 1, 2))
        txns.addExpense(cash.id, Money(Minor(300), rub), null, LocalDate.of(2026, 1, 3), note = "Кофе с молоком")

        assertEquals(listOf(byCard.id), txns.search("тинькофф").first().map { it.id })
        assertEquals(listOf(byCard.id), txns.search("ТИНЬКОФФ").first().map { it.id })
        assertEquals(listOf(groceries.id), txns.search("продукты").first().map { it.id })
        assertEquals(1, txns.search("кофе").first().size)
        assertEquals(emptyList<String>(), txns.search("зпрлдг").first().map { it.id })
        db.close()
    }
}
