package com.corriente.data.sync

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.corriente.data.backup.BackupRepository
import com.corriente.data.db.AppDatabase
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.DeleteResult
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
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/**
 * I-26 (docs/SYNC_DESIGN.md §9, S2): три устройства, случайные правки через настоящие репозитории и обмен файлами
 * в случайном порядке. После того как все обменялись до тишины, содержимое таблиц данных совпадает, и каждая БД
 * проходит проверку целостности бэкапа.
 */
class SyncConvergenceInstrumentedTest {

    private class Device(val name: String, val db: AppDatabase, val engine: SyncEngine) {
        val accounts = AccountRepository(db.accountDao())
        val categories = CategoryRepository(db.categoryDao())
        val txns = TxnRepository(db.txnDao(), db.accountDao())
    }

    private lateinit var folderRoot: File
    private val devices = mutableListOf<Device>()
    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")
    private var clock = 1_000_000L

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        folderRoot = File(ctx.cacheDir, "sync-test-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        devices.forEach { it.db.close() }
        folderRoot.deleteRecursively()
    }

    private fun newDevice(name: String): Device {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .addCallback(AppDatabase.seedCallback())
            .allowMainThreadQueries()
            .build()
        // Часы устройств немного расходятся — HLC должен с этим справляться.
        val skew = devices.size * 37L
        return Device(name, db, SyncEngine(db, FileSyncFolder(folderRoot)) { clock + skew }).also { devices += it }
    }

    /** Содержимое таблиц данных — то, что должно совпасть на всех устройствах. */
    private fun dump(db: AppDatabase): Map<String, List<String>> {
        val sql = db.openHelper.writableDatabase
        return listOf("currency", "account", "category", "txn", "budget", "recurrence", "import_batch", "import_alias", "category_redirect")
            .associateWith { table ->
                sql.query("SELECT * FROM `$table`").use { c ->
                    buildList {
                        while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { "${c.getColumnName(it)}=${c.getString(it)}" })
                    }.sorted()
                }
            } + ("app_setting" to sql.query("SELECT key, value FROM app_setting WHERE key NOT LIKE 'sync.%'").use { c ->
                buildList { while (c.moveToNext()) add("${c.getString(0)}=${c.getString(1)}") }.sorted()
            })
    }

    /** Обмен до тишины: круг, в котором никто ничего не выгрузил и не применил. Не наступает — это и есть дефект. */
    private suspend fun settle() {
        repeat(30) {
            var busy = false
            devices.forEach { d ->
                val r = d.engine.sync()
                clock += 10
                if (r.exported > 0 || r.applied > 0 || r.refused > 0) busy = true
            }
            if (!busy) return
        }
        throw AssertionError("devices never went quiet (ping-pong between devices)")
    }

    private suspend fun assertConverged() {
        settle()
        val first = dump(devices.first().db)
        devices.drop(1).forEach { d ->
            val other = dump(d.db)
            val diff = first.keys.mapNotNull { table ->
                val onlyFirst = first.getValue(table) - other.getValue(table).toSet()
                val onlyOther = other.getValue(table) - first.getValue(table).toSet()
                if (onlyFirst.isEmpty() && onlyOther.isEmpty()) null
                else "$table:\n  ${devices.first().name}: $onlyFirst\n  ${d.name}: $onlyOther"
            }
            if (diff.isNotEmpty()) {
                val meta = devices.joinToString("\n") { dev ->
                    val q = dev.db.openHelper.writableDatabase
                    "${dev.name}: meta=" + q.query("SELECT table_name, pk, hlc, deleted FROM sync_row_meta WHERE table_name='category' AND pk='seed-exp-transport'").use { c ->
                        buildList { while (c.moveToNext()) add("${c.getString(2)} del=${c.getInt(3)}") }
                    } + " redirect=" + q.query("SELECT from_id, into_id FROM category_redirect WHERE from_id='seed-exp-transport' OR into_id='seed-exp-transport'").use { c ->
                        buildList { while (c.moveToNext()) add("${c.getString(0)}->${c.getString(1)}") }
                    } + " outbox=" + q.query("SELECT COUNT(*) FROM sync_outbox").use { c -> c.moveToFirst(); c.getInt(0) } +
                        " refs=" + q.query("SELECT COUNT(*) FROM txn WHERE category_id='seed-exp-transport'").use { c -> c.moveToFirst(); c.getInt(0) }
                }
                throw AssertionError("${d.name} differs:\n${diff.joinToString("\n")}\n$meta")
            }
        }
        devices.forEach { d ->
            val problems = BackupRepository.validate(BackupRepository(d.db).buildPayload())
            assertTrue("${d.name}: $problems", problems.isEmpty())
            assertTrue("${d.name} still has deferred changes", d.db.syncDao().deferred().isEmpty())
        }
    }

    private suspend fun join(primary: Device, vararg others: Device) {
        primary.engine.enable(primary.name, primary = true, baseline = true)
        primary.engine.sync()
        others.forEach { it.engine.enable(it.name, primary = false, baseline = false); it.engine.sync() }
    }

    @Test
    fun basicEditsFlowBothWays() = runBlocking {
        val phone = newDevice("phone")
        val tablet = newDevice("tablet")
        val cash = phone.accounts.create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0)
        join(phone, tablet)

        val t1 = tablet.txns.addExpense(cash.id, Money(Minor(500), rub), "seed-exp-groceries", LocalDate.of(2026, 10, 1))
        tablet.engine.sync(); phone.engine.sync()
        assertEquals(500L, phone.txns.getById(t1.id)?.let { (it as com.corriente.data.model.Txn.Expense).amount.amount.raw })

        phone.txns.deleteById(t1.id)
        phone.engine.sync(); tablet.engine.sync()
        assertEquals(null, tablet.txns.getById(t1.id))
        assertConverged()
    }

    @Test
    fun concurrentEditOfOneTransactionLastWriterWins() = runBlocking {
        val phone = newDevice("phone")
        val tablet = newDevice("tablet")
        val cash = phone.accounts.create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0)
        val t = phone.txns.addExpense(cash.id, Money(Minor(100), rub), null, LocalDate.of(2026, 10, 1))
        join(phone, tablet)

        phone.txns.updateEntry(t.id, cash.id, Money(Minor(200), rub), null, LocalDate.of(2026, 10, 1), "phone")
        phone.engine.sync()
        clock += 1_000
        tablet.txns.updateEntry(t.id, cash.id, Money(Minor(300), rub), null, LocalDate.of(2026, 10, 1), "tablet") // позже
        assertConverged()
        assertEquals("tablet", phone.txns.getById(t.id)?.note)
    }

    @Test
    fun sameNamedCategoriesCreatedOnTwoDevicesBecomeOne() = runBlocking {
        val phone = newDevice("phone")
        val tablet = newDevice("tablet")
        val cash = phone.accounts.create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0)
        join(phone, tablet)

        val a = phone.categories.create("Такси", CategoryKind.EXPENSE, null, 0)
        val b = tablet.categories.create("Такси", CategoryKind.EXPENSE, null, 0)
        val ta = phone.txns.addExpense(cash.id, Money(Minor(1), rub), a.id, LocalDate.of(2026, 10, 1))
        val tb = tablet.txns.addExpense(cash.id, Money(Minor(2), rub), b.id, LocalDate.of(2026, 10, 1))
        assertConverged()

        val winner = minOf(a.id, b.id)
        val taxi = phone.categories.observeActive().first().filter { it.name == "Такси" }
        assertEquals(listOf(winner), taxi.map { it.id })
        assertEquals(winner, (phone.txns.getById(ta.id) as com.corriente.data.model.Txn.Expense).categoryId)
        assertEquals(winner, (phone.txns.getById(tb.id) as com.corriente.data.model.Txn.Expense).categoryId)
    }

    @Test
    fun mergeOnOneDeviceWhileTheOtherAddsToTheMergedCategory() = runBlocking {
        val phone = newDevice("phone")
        val tablet = newDevice("tablet")
        val cash = phone.accounts.create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0)
        val taxi = phone.categories.create("Такси", CategoryKind.EXPENSE, null, 0)
        join(phone, tablet)

        phone.categories.mergeInto(taxi.id, "seed-exp-transport")
        val late = tablet.txns.addExpense(cash.id, Money(Minor(7), rub), taxi.id, LocalDate.of(2026, 10, 2))
        assertConverged()
        assertEquals("seed-exp-transport", (phone.txns.getById(late.id) as com.corriente.data.model.Txn.Expense).categoryId)
        assertTrue(phone.categories.observeActive().first().none { it.id == taxi.id })
    }

    @Test
    fun deletingACategoryThatGotUsedElsewhereKeepsIt() = runBlocking {
        val phone = newDevice("phone")
        val tablet = newDevice("tablet")
        val cash = phone.accounts.create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0)
        val gym = phone.categories.create("Зал", CategoryKind.EXPENSE, null, 0)
        join(phone, tablet)

        assertEquals(DeleteResult.Deleted, phone.categories.deleteIfUnused(gym.id))
        val used = tablet.txns.addExpense(cash.id, Money(Minor(9), rub), gym.id, LocalDate.of(2026, 10, 3))
        assertConverged()
        assertEquals(gym.id, (phone.txns.getById(used.id) as com.corriente.data.model.Txn.Expense).categoryId)
    }

    // Чужие часы, ушедшие на века вперёд, не утаскивают за собой наши: следующая своя правка всё равно получает
    // нормальную версию, а LWW по-прежнему работает.
    @Test
    fun farFutureClockOfAnotherDeviceDoesNotPoisonOurs() = runBlocking {
        val phone = newDevice("phone")
        val tablet = newDevice("tablet")
        val cash = phone.accounts.create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0)
        join(phone, tablet)
        val tabletEngine = SyncEngine(tablet.db, FileSyncFolder(folderRoot)) { 9_000_000_000_000L } // ~2255 год
        tablet.txns.addExpense(cash.id, Money(Minor(1), rub), null, LocalDate.of(2026, 10, 1))
        tabletEngine.sync()
        phone.engine.sync()
        val hlc = phone.db.appSettingDao().get("sync.hlc")!!
        assertTrue(hlc, Hlc.decode(hlc).wallMs < 9_000_000_000_000L)
    }

    @Test
    fun randomOperationsOnThreeDevicesConverge() = runBlocking {
        for (seed in 1..6) {
            devices.forEach { it.db.close() }
            devices.clear()
            folderRoot.deleteRecursively(); folderRoot.mkdirs()
            runRandom(seed)
        }
    }

    private suspend fun runRandom(seed: Int) {
        val rnd = Random(seed)
        val a = newDevice("a$seed")
        val b = newDevice("b$seed")
        val c = newDevice("c$seed")
        a.accounts.create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0)
        a.accounts.create("Доллары", usd, AccountKind.CASH, Money(Minor(0), usd), 0)
        join(a, b, c)

        val names = listOf("Такси", "Кофе", "Аптека", "Кино")
        repeat(150) { step ->
            clock += rnd.nextLong(1, 50)
            val d = devices[rnd.nextInt(devices.size)]
            val accounts = d.accounts.observeActive().first()
            val categories = d.categories.observeActive().first().filter { it.kind == CategoryKind.EXPENSE }
            val txns = d.txns.observeAll().first().filterIsInstance<com.corriente.data.model.Txn.Expense>()
            runCatching {
                when (rnd.nextInt(10)) {
                    0, 1, 2 -> if (accounts.isNotEmpty()) {
                        val acc = accounts.random(rnd)
                        d.txns.addExpense(acc.id, Money(Minor(rnd.nextLong(1, 10_000)), acc.currency), categories.randomOrNull(rnd)?.id, LocalDate.of(2026, 10, 1 + rnd.nextInt(28)), "s$step")
                    }
                    3 -> txns.randomOrNull(rnd)?.let { t ->
                        d.txns.updateEntry(t.id, t.accountId, Money(Minor(rnd.nextLong(1, 10_000)), t.amount.currency), categories.randomOrNull(rnd)?.id, t.date, "e$step")
                    }
                    4 -> txns.randomOrNull(rnd)?.let { d.txns.deleteById(it.id) }
                    5 -> d.categories.create(names.random(rnd), CategoryKind.EXPENSE, null, rnd.nextInt())
                    6 -> if (categories.size > 2) {
                        val leafs = categories.filter { cat -> categories.none { it.parentId == cat.id } && cat.parentId == null }
                        val from = leafs.randomOrNull(rnd)
                        val into = categories.filter { it.id != from?.id }.randomOrNull(rnd)
                        if (from != null && into != null) d.categories.mergeInto(from.id, into.id)
                    }
                    7 -> categories.randomOrNull(rnd)?.let { d.categories.deleteIfUnused(it.id) }
                    8 -> accounts.randomOrNull(rnd)?.let { d.accounts.update(it.id, "${it.name.take(8)}$step", it.color, it.icon, it.includeInTotal) }
                    else -> d.engine.sync()
                }
            }.exceptionOrNull()?.let { e ->
                // Нарушение правил самим тестом (например, слияние категории с подкатегориями) — не дефект синхронизации.
                if (e is SyncIntegrityException) throw e
            }
        }
        assertConverged()
    }
}
