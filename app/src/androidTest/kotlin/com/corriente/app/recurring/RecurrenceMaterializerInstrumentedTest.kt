package com.corriente.app.recurring

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.corriente.data.db.AppDatabase
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.db.entity.RecurrenceRuleType
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.recurrence.RecurrenceRule
import com.corriente.data.repository.RecurrenceRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * `[БД]` R2.4: [RecurrenceMaterializer] на настоящем Room — создание только за прошедшие даты,
 * догон нескольких пропущенных периодов, идемпотентность повторного запуска в тот же день.
 * Не использует `androidx.work:work-testing` (не в списке разрешённых зависимостей,
 * BUILD_PLAN.md §1.3) — тестируется сама логика материализации, которую `RecurrenceWorker`
 * лишь тонко оборачивает под `CoroutineWorker`.
 */
class RecurrenceMaterializerInstrumentedTest {

    private lateinit var db: AppDatabase
    private lateinit var txns: TxnRepository
    private lateinit var recurrences: RecurrenceRepository

    private val rub = CurrencyCode("RUB")

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        txns = TxnRepository(db.txnDao(), db.accountDao())
        recurrences = RecurrenceRepository(db.recurrenceDao())
        runBlocking {
            db.currencyDao().insertAll(listOf(CurrencyEntity("RUB", 2, 2, "₽", isActive = true)))
            db.accountDao().insert(AccountEntity("cash", "Наличные", "RUB", AccountKind.CASH, 0, 0))
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun neverCreatesTransactionsForFutureDates() = runBlocking {
        recurrences.create(
            kind = TxnKind.EXPENSE, accountId = "cash", categoryId = null,
            amount = Money(Minor(1_000_00), rub), note = null,
            rule = RecurrenceRule.DayOfMonth(1), startsOn = LocalDate.of(2026, 10, 1),
        )
        // nextRunOn = 2026-10-01, "сегодня" заведомо раньше.
        RecurrenceMaterializer.materializeDue(LocalDate.of(2026, 9, 2), recurrences, txns, "[авто]")

        assertTrue(txns.observeAll().first().isEmpty())
    }

    @Test
    fun catchesUpMultipleMissedMonthsInOneRun() = runBlocking {
        val rule = recurrences.create(
            kind = TxnKind.EXPENSE, accountId = "cash", categoryId = null,
            amount = Money(Minor(5_000_00), rub), note = "Аренда",
            rule = RecurrenceRule.DayOfMonth(1), startsOn = LocalDate.of(2026, 6, 1),
        )
        // Устройство "не запускалось" с июня — сегодня уже сентябрь.
        RecurrenceMaterializer.materializeDue(LocalDate.of(2026, 9, 2), recurrences, txns, "[авто]")

        val created = txns.observeAll().first()
        assertEquals(4, created.size) // 1 июня, 1 июля, 1 августа, 1 сентября
        assertEquals(
            listOf("2026-06-01", "2026-07-01", "2026-08-01", "2026-09-01"),
            created.map { it.date.toString() }.sorted(),
        )
        assertTrue(created.all { it.note?.contains("Аренда") == true })

        val updated = recurrences.getById(rule.id)!!
        assertEquals(LocalDate.of(2026, 10, 1), updated.nextRunOn)
        assertEquals(created.maxByOrNull { it.date }!!.id, updated.lastCreatedTxnId)
    }

    // I-19-стиль: повторный запуск воркера в тот же день не создаёт дублей.
    @Test
    fun rerunningOnTheSameDayDoesNotDuplicate() = runBlocking {
        recurrences.create(
            kind = TxnKind.INCOME, accountId = "cash", categoryId = null,
            amount = Money(Minor(200_00), rub), note = null,
            rule = RecurrenceRule.EveryNDays(1), startsOn = LocalDate.of(2026, 9, 2),
        )
        val today = LocalDate.of(2026, 9, 2)

        RecurrenceMaterializer.materializeDue(today, recurrences, txns, "[авто]")
        RecurrenceMaterializer.materializeDue(today, recurrences, txns, "[авто]")
        RecurrenceMaterializer.materializeDue(today, recurrences, txns, "[авто]")

        assertEquals(1, txns.observeAll().first().size)
    }

    @Test
    fun everyNDaysRuleCreatesOnItsOwnSchedule() = runBlocking {
        recurrences.create(
            kind = TxnKind.EXPENSE, accountId = "cash", categoryId = null,
            amount = Money(Minor(300_00), rub), note = null,
            rule = RecurrenceRule.EveryNDays(3), startsOn = LocalDate.of(2026, 9, 1),
        )
        // nextRunOn = 2026-09-01; сегодня 2026-09-07 -> должны появиться 01, 04, 07.
        RecurrenceMaterializer.materializeDue(LocalDate.of(2026, 9, 7), recurrences, txns, "[авто]")

        val created = txns.observeAll().first()
        assertEquals(
            listOf("2026-09-01", "2026-09-04", "2026-09-07"),
            created.map { it.date.toString() }.sorted(),
        )
    }

    @Test
    fun ruleTypeRoundTripsThroughRepository() = runBlocking {
        val created = recurrences.create(
            kind = TxnKind.EXPENSE, accountId = "cash", categoryId = null,
            amount = Money(Minor(100_00), rub), note = null,
            rule = RecurrenceRule.DayOfMonth(31), startsOn = LocalDate.of(2026, 1, 1),
        )
        val fromDb = recurrences.getById(created.id)!!
        assertEquals(RecurrenceRule.DayOfMonth(31), fromDb.rule)

        val entity = db.recurrenceDao().getById(created.id)!!
        assertEquals(RecurrenceRuleType.DAY_OF_MONTH, entity.ruleType)
        assertEquals(31, entity.dayOfMonth)
    }
}
