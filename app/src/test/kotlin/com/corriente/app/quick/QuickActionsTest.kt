package com.corriente.app.quick

import com.corriente.app.ui.accounts.FakeAccountDao
import com.corriente.app.ui.txnentry.FakeTxnDao
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.repository.TxnRepository
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** F0.6 — логика записи из окон быстрого ввода: ошибка репозитория не должна ронять процесс. */
class QuickActionsTest {

    private val day = LocalDate.of(2026, 1, 1)
    private val rub = Money(Minor(100), CurrencyCode("RUB"))

    private suspend fun repo(txnDao: FakeTxnDao): TxnRepository {
        val accountDao = FakeAccountDao()
        accountDao.insert(
            AccountEntity(id = "acc", name = "Наличные", currencyCode = "RUB", kind = AccountKind.CASH, color = 0),
        )
        return TxnRepository(txnDao, accountDao)
    }

    @Test
    fun `a repository failure is returned as Result_failure, not thrown`() = runTest {
        val txnDao = FakeTxnDao().apply { failWith = IllegalStateException("db down") }
        val result = addQuickExpense(repo(txnDao), "acc", rub, categoryId = null, today = day)
        assertTrue(result.isFailure)
        assertTrue(txnDao.rows.value.isEmpty())
    }

    @Test
    fun `a successful write records exactly one expense`() = runTest {
        val txnDao = FakeTxnDao()
        val result = addQuickExpense(repo(txnDao), "acc", rub, categoryId = "cat", today = day)
        assertTrue(result.isSuccess)
        assertFalse(txnDao.rows.value.isEmpty())
    }
}
