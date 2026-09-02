package com.corriente.data.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.model.toDomain
import com.corriente.data.usecase.accountBalance
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** F2.1 — SQL-агрегаты `observeAccountDeltas` / `observeRange` совпадают с чистыми функциями. */
class TxnDaoAggregatesInstrumentedTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            db.currencyDao().insertAll(
                listOf(
                    CurrencyEntity("RUB", 2, 2, "₽", isActive = true),
                    CurrencyEntity("USD", 2, 2, "$", isActive = true),
                ),
            )
            db.accountDao().insert(AccountEntity("cash", "Наличные", "RUB", AccountKind.CASH, 10_000_00, 0))
            db.accountDao().insert(AccountEntity("card", "Карта", "USD", AccountKind.CARD, 0, 0))
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun deltasMatchThePureBalanceFunction() = runBlocking {
        val txns = listOf(
            TxnEntity("i", TxnKind.INCOME, "2026-01-01", 0, 0, "cash", 5_000_00, "RUB"),
            TxnEntity("e", TxnKind.EXPENSE, "2026-02-01", 0, 0, "cash", 1_250_00, "RUB"),
            TxnEntity(
                "t", TxnKind.TRANSFER, "2026-03-01", 0, 0, "cash", 8_695_00, "RUB",
                toAccountId = "card", toAmountMinor = 100_00, toCurrencyCode = "USD",
            ),
        )
        db.txnDao().insertAll(txns)

        val deltas = db.txnDao().observeAccountDeltas().first().associate { it.accountId to it.deltaMinor }
        val accounts = (db.accountDao().observeActive().first()).associateBy { it.id }
        val domain = txns.map { it.toDomain() }

        listOf("cash", "card").forEach { id ->
            val acc = accounts.getValue(id).toDomain()
            val fromSql = Money(Minor(acc.openingBalance.amount.raw + (deltas[id] ?: 0L)), acc.currency)
            assertEquals(accountBalance(acc, domain), fromSql)
        }

        // observeRange отсекает по дате
        assertEquals(1, db.txnDao().observeRange("2026-02-01", "2026-02-28").first().size)
        assertEquals(true, db.txnDao().observeAnyExist().first())
    }
}
