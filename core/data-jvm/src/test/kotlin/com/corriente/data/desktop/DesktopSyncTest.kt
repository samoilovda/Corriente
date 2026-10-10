package com.corriente.data.desktop

import com.corriente.data.backup.BackupRepository
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.model.Txn
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.data.sync.FileSyncFolder
import com.corriente.data.sync.SyncEngine
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

/**
 * Десктоп (D2): тот же слой данных на JVM со встроенным SQLite — засев, триггеры, синхронизация «телефон ↔ ноутбук»
 * через общую папку, проверка целостности. Тест идёт без эмулятора.
 */
class DesktopSyncTest {

    @get:Rule val tmp = TemporaryFolder()
    private val rub = CurrencyCode("RUB")

    @Test
    fun seededDatabaseLooksLikeTheAndroidOne() = runBlocking {
        val db = DesktopDatabase.open(tmp.newFile("a.db").also { it.delete() })
        val categories = db.categoryDao().observeAll().first()
        assertTrue(categories.any { it.id == "seed-exp-groceries" })
        assertTrue(db.currencyDao().observeAll().first().any { it.code == "RUB" && it.isActive })
        db.close()
    }

    @Test
    fun phoneAndLaptopSyncThroughAFolder() = runBlocking {
        val folder = FileSyncFolder(tmp.newFolder("sync"))
        val phone = DesktopDatabase.open(tmp.newFile("phone.db").also { it.delete() })
        val laptop = DesktopDatabase.open(tmp.newFile("laptop.db").also { it.delete() })
        val phoneEngine = SyncEngine(phone, folder)
        val laptopEngine = SyncEngine(laptop, folder)

        val cash = AccountRepository(phone.accountDao()).create("Нал", rub, AccountKind.CASH, Money(Minor(100_00), rub), 0)
        TxnRepository(phone.txnDao(), phone.accountDao()).addExpense(cash.id, Money(Minor(5_00), rub), "seed-exp-groceries", LocalDate.of(2026, 10, 11))
        phoneEngine.enable("phone", primary = true, baseline = true)
        phoneEngine.sync()

        laptopEngine.enable("laptop", primary = false, baseline = false)
        laptopEngine.sync()
        val laptopTxns = TxnRepository(laptop.txnDao(), laptop.accountDao())
        assertEquals(1, laptopTxns.observeAll().first().size)

        // ввод на ноутбуке доходит до телефона
        val t = laptopTxns.addIncome(cash.id, Money(Minor(50_00), rub), null, LocalDate.of(2026, 10, 11), "с ноутбука")
        laptopEngine.sync()
        phoneEngine.sync()
        val onPhone = TxnRepository(phone.txnDao(), phone.accountDao()).getById(t.id) as Txn.Income
        assertEquals("с ноутбука", onPhone.note)

        listOf(phone, laptop).forEach { db ->
            assertTrue(BackupRepository.validate(BackupRepository(db).buildPayload()).isEmpty())
            db.close()
        }
    }
}
