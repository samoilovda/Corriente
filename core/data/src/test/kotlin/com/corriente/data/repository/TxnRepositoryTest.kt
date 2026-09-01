package com.corriente.data.repository

import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.model.Txn
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TxnRepositoryTest {

    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")
    private val day = LocalDate.of(2026, 5, 1)

    private fun setup(): Triple<TxnRepository, FakeTxnDao, FakeAccountDao> {
        val txnDao = FakeTxnDao()
        val accountDao = FakeAccountDao()
        val repo = TxnRepository(txnDao, accountDao)
        return Triple(repo, txnDao, accountDao)
    }

    private suspend fun FakeAccountDao.add(id: String, code: String) = insert(
        AccountEntity(id = id, name = id, currencyCode = code, kind = AccountKind.CASH, openingBalanceMinor = 0, color = 0),
    )

    @Test
    fun `updateEntry changes amount, category, date and note, keeping the kind`() = runTest {
        val (repo, dao, accounts) = setup()
        accounts.add("acc", "RUB")
        val created = repo.addExpense("acc", Money(Minor(500_00), rub), "cat-a", day, "старое")

        repo.updateEntry(
            id = created.id,
            accountId = "acc",
            amount = Money(Minor(742_50), rub),
            categoryId = "cat-b",
            date = day.plusDays(3),
            note = "новое",
        )

        val row = dao.getById(created.id)!!
        assertEquals(TxnKind.EXPENSE, row.kind)
        assertEquals(74250L, row.amountMinor)
        assertEquals("cat-b", row.categoryId)
        assertEquals(day.plusDays(3).toString(), row.date)
        assertEquals("новое", row.note)
    }

    @Test
    fun `updateEntry rejects an amount whose currency does not match the target account`() = runTest {
        val (repo, _, accounts) = setup()
        accounts.add("rub", "RUB")
        accounts.add("usd", "USD")
        val created = repo.addIncome("rub", Money(Minor(100_00), rub), null, day, null)

        val error = runCatching {
            repo.updateEntry(created.id, "usd", Money(Minor(100_00), rub), null, day, null)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `updateEntry refuses to touch a transfer`() = runTest {
        val (repo, _, accounts) = setup()
        accounts.add("a", "RUB")
        accounts.add("b", "USD")
        val transfer = repo.addTransfer("a", Money(Minor(1_000_00), rub), "b", Money(Minor(10_00), usd), day, null) as Txn.Transfer

        val error = runCatching {
            repo.updateEntry(transfer.id, "a", Money(Minor(1_000_00), rub), null, day, null)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    // T2.1 / I-7а: перевод — одна строка с двумя суммами; они и есть источник истины.
    @Test
    fun `addTransfer stores both amounts on a single transfer row`() = runTest {
        val (repo, dao, accounts) = setup()
        accounts.add("rub", "RUB")
        accounts.add("usd", "USD")

        val transfer = repo.addTransfer(
            "rub", Money(Minor(8_695_00), rub), "usd", Money(Minor(100_00), usd), day, "обмен",
        ) as Txn.Transfer

        assertEquals(1, dao.rows.value.size)
        val row = dao.rows.value.single()
        assertEquals("rub", row.accountId)
        assertEquals(869500L, row.amountMinor)
        assertEquals("RUB", row.currencyCode)
        assertEquals("usd", row.toAccountId)
        assertEquals(10000L, row.toAmountMinor)
        assertEquals("USD", row.toCurrencyCode)
        assertNull(row.categoryId) // I-11: у перевода нет категории
        assertEquals(Money(Minor(8_695_00), rub), transfer.fromAmount)
        assertEquals(Money(Minor(100_00), usd), transfer.toAmount)
    }

    @Test
    fun `addTransfer rejects self-transfer, non-positive amounts and currency mismatch`() = runTest {
        val (repo, _, accounts) = setup()
        accounts.add("rub", "RUB")
        accounts.add("usd", "USD")

        assertTrue(
            runCatching { repo.addTransfer("rub", Money(Minor(1), rub), "rub", Money(Minor(1), rub), day, null) }
                .exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { repo.addTransfer("rub", Money(Minor(0), rub), "usd", Money(Minor(1), usd), day, null) }
                .exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching { repo.addTransfer("rub", Money(Minor(1), usd), "usd", Money(Minor(1), usd), day, null) }
                .exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `updateTransfer rewrites both legs, updateTransfer refuses a non-transfer`() = runTest {
        val (repo, dao, accounts) = setup()
        accounts.add("rub", "RUB")
        accounts.add("usd", "USD")
        accounts.add("eur", "EUR")
        val transfer = repo.addTransfer("rub", Money(Minor(1_000_00), rub), "usd", Money(Minor(10_00), usd), day, null) as Txn.Transfer

        repo.updateTransfer(
            transfer.id, "rub", Money(Minor(2_000_00), rub), "eur", Money(Minor(20_00), CurrencyCode("EUR")),
            day.plusDays(1), "правка",
        )
        val row = dao.getById(transfer.id)!!
        assertEquals(200000L, row.amountMinor)
        assertEquals("eur", row.toAccountId)
        assertEquals(2000L, row.toAmountMinor)
        assertEquals("EUR", row.toCurrencyCode)

        val expense = repo.addExpense("rub", Money(Minor(1), rub), null, day, null)
        assertTrue(
            runCatching {
                repo.updateTransfer(expense.id, "rub", Money(Minor(1), rub), "usd", Money(Minor(1), usd), day, null)
            }.exceptionOrNull() is IllegalArgumentException,
        )
    }

    @Test
    fun `deleteById removes the transaction`() = runTest {
        val (repo, _, accounts) = setup()
        accounts.add("acc", "RUB")
        val created = repo.addExpense("acc", Money(Minor(1), rub), null, day, null)

        repo.deleteById(created.id)
        assertNull(repo.getById(created.id))
        assertTrue(repo.observeAll().first().isEmpty())
    }
}
