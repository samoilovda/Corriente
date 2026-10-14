package com.corriente.data.desktop

import com.corriente.data.backup.BackupPayload
import com.corriente.data.backup.BackupRepository
import com.corriente.data.db.AppDatabase
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.BudgetRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.data.sync.FileSyncFolder
import com.corriente.data.sync.SyncEngine
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import kotlin.random.Random

/**
 * S5 (docs/SYNC_DESIGN.md §7): снимки и очистка папки. Три устройства на JVM, общее время `now` подменяется, чтобы
 * «прожить» 91 день за миг.
 */
class SyncCleanupTest {

    @get:Rule val tmp = TemporaryFolder()

    private val rub = CurrencyCode("RUB")
    private val day = 24L * 60 * 60 * 1000
    private var now = 1_700_000_000_000L
    private val opened = mutableListOf<AppDatabase>()
    private lateinit var root: File

    private inner class Device(val name: String) {
        val db: AppDatabase = DesktopDatabase.inMemory().also { opened += it }
        val engine = SyncEngine(db, FileSyncFolder(root)) { now }
        val accounts = AccountRepository(db.accountDao())
        val categories = CategoryRepository(db.categoryDao())
        val txns = TxnRepository(db.txnDao(), db.accountDao())
        val budgets = BudgetRepository(db.budgetDao())
        var id: String = ""

        suspend fun join(primary: Boolean) {
            engine.enable(name, primary = primary, baseline = primary)
            engine.sync()
            id = engine.deviceId()!!
        }

        /** Одна правка и обмен — каждая даёт отдельный файл в `changes/<id>`. */
        suspend fun addExpense(accountId: String, minor: Long) {
            txns.addExpense(accountId, Money(Minor(minor), rub), null, LocalDate.of(2026, 10, 1))
            now += 1_000
            engine.sync()
        }

        fun files(): List<String> = File(root, "changes/$id").list()?.filter { !it.startsWith(".") }.orEmpty().sorted()
    }

    @Before
    fun setUp() {
        root = tmp.newFolder("sync")
    }

    @After
    fun tearDown() = opened.forEach { it.close() }

    private suspend fun state(db: AppDatabase): BackupPayload {
        val p = BackupRepository(db).buildPayload()
        // Порядок строк в таблицах не важен, время выгрузки и настройки устройства — тоже.
        return p.copy(
            exportedAt = 0,
            accounts = p.accounts.sortedBy { it.id }, categories = p.categories.sortedBy { it.id },
            transactions = p.transactions.sortedBy { it.id }, budgets = p.budgets.sortedBy { it.id },
            recurrences = p.recurrences.sortedBy { it.id }, importBatches = p.importBatches.sortedBy { it.id },
            importAliases = p.importAliases.sortedBy { it.toString() }, currencies = p.currencies.sortedBy { it.code },
            appSettings = p.appSettings.sortedBy { it.key },
        )
    }

    private suspend fun settle(devices: List<Device>) {
        repeat(30) {
            var busy = false
            devices.forEach { d ->
                val r = d.engine.sync()
                now += 10
                if (r.exported > 0 || r.applied > 0 || r.refused > 0) busy = true
            }
            if (!busy) return
        }
        throw AssertionError("devices never went quiet")
    }

    private suspend fun assertSame(devices: List<Device>) {
        settle(devices)
        val first = state(devices.first().db)
        devices.drop(1).forEach {
            val other = state(it.db)
            if (first != other) {
                val sections = mapOf<String, (BackupPayload) -> List<Any>>(
                    "accounts" to { p -> p.accounts }, "categories" to { p -> p.categories }, "transactions" to { p -> p.transactions },
                    "budgets" to { p -> p.budgets }, "appSettings" to { p -> p.appSettings },
                )
                val diff = sections.mapNotNull { (name, get) ->
                    val onlyFirst = get(first) - get(other).toSet()
                    val onlyOther = get(other) - get(first).toSet()
                    if (onlyFirst.isEmpty() && onlyOther.isEmpty()) null
                    else "$name:\n  ${devices.first().name}: $onlyFirst\n  ${it.name}: $onlyOther"
                }
                throw AssertionError("${it.name} differs from ${devices.first().name}:\n${diff.joinToString("\n")}")
            }
        }
        devices.forEach {
            assertTrue(BackupRepository.validate(BackupRepository(it.db).buildPayload()).isEmpty())
            assertTrue(it.db.syncDao().deferred().isEmpty())
        }
    }

    @Test
    fun ownFilesAreDeletedOnlyAfterEveryoneAppliedThem() = runBlocking {
        val a = Device("a"); val b = Device("b"); val c = Device("c")
        val cash = a.accounts.create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0)
        a.join(primary = true); b.join(primary = false); c.join(primary = false)
        repeat(5) { a.addExpense(cash.id, 100L + it) }
        b.engine.sync(); c.engine.sync()           // обе карточки: «видел до последнего файла a»
        val firstBatch = a.files()
        assertTrue(firstBatch.size >= 5)

        repeat(3) { a.addExpense(cash.id, 500L + it) }
        b.engine.sync()                            // b применил всё, c — нет
        val before = a.files()
        val result = a.engine.cleanup()
        val after = a.files()
        assertTrue("c ещё не применил новые файлы — их удалять нельзя", result.deletedFiles > 0 && after.isNotEmpty())
        assertTrue(after.size < before.size)
        // не удалён ни один файл, которого c ещё не видел: новые 3 + последний обмен
        assertTrue(after.containsAll(before.takeLast(3)))

        c.engine.sync(); b.engine.sync(); a.engine.sync()
        val again = a.engine.cleanup()
        assertTrue(again.deletedFiles > 0)
        assertTrue("после того как все применили — файлов почти не остаётся", a.files().size <= 1)
        assertSame(listOf(a, b, c))
    }

    @Test
    fun newDeviceJoinsAfterCleanupThroughSnapshot() = runBlocking {
        val a = Device("a"); val b = Device("b"); val d = Device("d")
        val cash = a.accounts.create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0)
        a.join(primary = true); b.join(primary = false)
        repeat(6) { a.addExpense(cash.id, 100L + it) }
        b.categories.create("Такси", CategoryKind.EXPENSE, null, 0)
        b.engine.sync(); a.engine.sync(); b.engine.sync()
        a.engine.cleanup(); b.engine.cleanup()
        assertTrue("файлы a убраны", a.files().size <= 1)

        d.join(primary = false)                    // файлов нет — только снимки
        a.addExpense(cash.id, 999)                 // и хвост после снимка
        assertSame(listOf(a, b, d))
        assertEquals(7, d.txns.observeAll().first().size)
        assertTrue(File(root, "snapshots/${a.id}.json").isFile)
    }

    @Test
    fun deviceAbsentFor91DaysDoesNotBlockCleanupAndRejoinsThroughSnapshot() = runBlocking {
        val a = Device("a"); val b = Device("b"); val c = Device("c")
        val cash = a.accounts.create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0)
        a.join(primary = true); b.join(primary = false); c.join(primary = false)
        a.addExpense(cash.id, 1); b.engine.sync(); c.engine.sync()

        now += 91 * day                            // c пропал на 91 день
        repeat(4) { a.addExpense(cash.id, 10L + it) }
        b.engine.sync(); a.engine.sync(); b.engine.sync()
        val result = a.engine.cleanup()
        assertTrue("пропавший c не блокирует очистку", result.deletedFiles > 0)
        assertTrue(a.files().size <= 1)
        b.engine.cleanup()

        c.txns.addExpense(cash.id, Money(Minor(77), rub), null, LocalDate.of(2026, 10, 2)) // правка c, пока он «спал»
        assertSame(listOf(a, b, c))                // c вернулся: снимок + хвост
        assertEquals(6, c.txns.observeAll().first().size)
    }

    @Test
    fun randomOperationsWithRandomCleanupsConverge() = runBlocking {
        for (seed in 1..(System.getenv("SYNC_CLEANUP_SEEDS")?.toIntOrNull() ?: 8)) {
            val rnd = Random(seed)
            root = tmp.newFolder("sync-$seed")
            val devices = listOf(Device("a$seed"), Device("b$seed"), Device("c$seed"))
            val (a, b, c) = devices
            val cash = a.accounts.create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0)
            a.join(primary = true); b.join(primary = false); c.join(primary = false)
            repeat(120) { step ->
                now += rnd.nextLong(1, 5_000)
                val d = devices[rnd.nextInt(3)]
                runCatching {
                    when (rnd.nextInt(9)) {
                        0, 1, 2 -> d.txns.addExpense(cash.id, Money(Minor(rnd.nextLong(1, 10_000)), rub), null, LocalDate.of(2026, 10, 1 + rnd.nextInt(28)), "s$step")
                        3 -> d.txns.observeAll().first().randomOrNull(rnd)?.let { d.txns.deleteById(it.id) }
                        4 -> d.categories.create("К${rnd.nextInt(4)}", CategoryKind.EXPENSE, null, rnd.nextInt())
                        5 -> d.budgets.create(null, Money(Minor(rnd.nextLong(1, 1_000)), rub), LocalDate.of(2026, 10, 1))
                        6 -> d.engine.cleanup()
                        // иногда устройство надолго «засыпает»: время идёт, оно не обменивается
                        7 -> if (rnd.nextInt(10) == 0) now += 100 * day
                        else -> d.engine.sync()
                    }
                }.exceptionOrNull()?.let { if (it is com.corriente.data.sync.SyncIntegrityException) throw it }
            }
            assertSame(devices)
        }
    }
}
