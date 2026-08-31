package com.corriente.data.repository

import com.corriente.data.db.entity.AccountKind
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRepositoryTest {

    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")

    private fun repo(dao: FakeAccountDao = FakeAccountDao()) = AccountRepository(dao) to dao

    private suspend fun AccountRepository.newRubAccount(dao: FakeAccountDao): String {
        val account = create(
            name = "Наличные",
            currency = rub,
            kind = AccountKind.CASH,
            openingBalance = Money(Minor(10_000_00), rub),
            color = 0,
        )
        return account.id
    }

    @Test
    fun `create rejects an opening balance in a different currency`() = runTest {
        val (repository, _) = repo()
        val error = runCatching {
            repository.create("X", rub, AccountKind.CARD, Money(Minor(1), usd), color = 0)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    // Приёмочный тест T1.3: смена валюты счёта с операциями отклоняется (I-23).
    @Test
    fun `changing the currency of an account that already has transactions is rejected`() = runTest {
        val (repository, dao) = repo()
        val id = repository.newRubAccount(dao)
        dao.markHasTransactions(id)

        val error = runCatching {
            repository.setCurrencyAndOpeningBalanceBeforeFirstUse(id, usd, Money(Minor(0), usd))
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("RUB", repository.getById(id)?.currency?.code)
    }

    @Test
    fun `changing the currency before the first transaction succeeds`() = runTest {
        val (repository, dao) = repo()
        val id = repository.newRubAccount(dao)

        repository.setCurrencyAndOpeningBalanceBeforeFirstUse(id, usd, Money(Minor(250_00), usd))

        val updated = repository.getById(id)!!
        assertEquals("USD", updated.currency.code)
        assertEquals(Money(Minor(250_00), usd), updated.openingBalance)
    }

    @Test
    fun `deleteIfUnused physically removes an account with no transactions`() = runTest {
        val (repository, dao) = repo()
        val id = repository.newRubAccount(dao)

        assertTrue(repository.deleteIfUnused(id))
        assertNull(repository.getById(id))
    }

    @Test
    fun `deleteIfUnused refuses an account that has transactions - archive instead`() = runTest {
        val (repository, dao) = repo()
        val id = repository.newRubAccount(dao)
        dao.markHasTransactions(id)

        assertFalse(repository.deleteIfUnused(id))
        assertEquals(id, repository.getById(id)?.id)
    }

    @Test
    fun `archive moves an account out of the active list and unarchive brings it back`() = runTest {
        val (repository, dao) = repo()
        val id = repository.newRubAccount(dao)

        repository.archive(id)
        assertTrue(repository.observeActive().first().isEmpty())
        assertEquals(listOf(id), repository.observeArchived().first().map { it.id })

        repository.unarchive(id)
        assertEquals(listOf(id), repository.observeActive().first().map { it.id })
        assertTrue(repository.observeArchived().first().isEmpty())
    }
}
