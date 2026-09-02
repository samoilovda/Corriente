package com.corriente.data.usecase

import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.model.Txn
import com.corriente.data.model.toDomain
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.FakeAccountDao
import com.corriente.data.repository.FakeTxnDao
import com.corriente.data.repository.TxnRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F2.1: защита от возврата O(счета × операции). 50 000 операций, 20 счетов — балансы обязаны
 * считаться за один проход (агрегат), а не сканом всей таблицы на каждый счёт.
 * Не микробенчмарк: бюджет намеренно щедрый, ловит только квадратичную регрессию.
 */
class AccountBalancePerfTest {

    @Test
    fun `balances over 50k transactions and 20 accounts compute in one pass`() = runBlocking {
        val accountDao = FakeAccountDao()
        val txnDao = FakeTxnDao()
        repeat(20) { i ->
            accountDao.insert(
                AccountEntity(
                    id = "acc-$i", name = "Счёт $i", currencyCode = "RUB", kind = AccountKind.CASH,
                    openingBalanceMinor = 1_000_00, color = 0,
                ),
            )
        }
        val txns = ArrayList<TxnEntity>(50_000)
        for (n in 0 until 50_000) {
            val acc = "acc-${n % 20}"
            // Чётные проходы по всем счетам — расход, нечётные — доход: у каждого счёта поровну.
            txns += TxnEntity(
                id = "t-$n", kind = if ((n / 20) % 2 == 0) TxnKind.EXPENSE else TxnKind.INCOME,
                date = "2026-01-%02d".format((n % 28) + 1), createdAt = n.toLong(), updatedAt = n.toLong(),
                accountId = acc, amountMinor = 100, currencyCode = "RUB",
            )
        }
        txnDao.insertAll(txns)

        val useCase = AccountBalanceUseCase(AccountRepository(accountDao), TxnRepository(txnDao, accountDao))

        val start = System.nanoTime()
        val balances = useCase.observeBalances().first()
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertEquals(20, balances.size)
        // Каждый счёт: поровну расходов и доходов по 1.00 → баланс = opening.
        balances.forEach { assertEquals(1_000_00L, it.balance.amount.raw) }
        // Сверка с чистой функцией на одном счёте.
        val acc0 = balances.first { it.account.id == "acc-0" }.account
        val domainTxns: List<Txn> = txns.filter { it.accountId == "acc-0" }.map { it.toDomain() }
        assertEquals(accountBalance(acc0, domainTxns), balances.first { it.account.id == "acc-0" }.balance)

        assertTrue("расчёт занял ${elapsedMs}ms — похоже на O(N·M)", elapsedMs < 3_000)
    }
}
