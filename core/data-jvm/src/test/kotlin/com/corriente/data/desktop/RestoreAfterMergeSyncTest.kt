package com.corriente.data.desktop

import com.corriente.data.backup.BackupRepository
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.model.Txn
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CategoryRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.data.sync.FileSyncFolder
import com.corriente.data.sync.SyncEngine
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

/**
 * W4: слил «Кафе» в «Еду», передумал и восстановил бэкап до слияния — при включённой синхронизации следующий обмен не
 * должен слить «Кафе» снова ни на этом устройстве, ни на другом.
 */
class RestoreAfterMergeSyncTest {

    @get:Rule val tmp = TemporaryFolder()
    private val rub = CurrencyCode("RUB")

    @Test
    fun restoredCategorySurvivesSyncOnBothDevices() = runBlocking {
        val folder = FileSyncFolder(tmp.newFolder("sync"))
        val phone = DesktopDatabase.open(tmp.newFile("phone.db").also { it.delete() })
        val laptop = DesktopDatabase.open(tmp.newFile("laptop.db").also { it.delete() })
        val phoneEngine = SyncEngine(phone, folder)
        val laptopEngine = SyncEngine(laptop, folder)

        val categories = CategoryRepository(phone.categoryDao())
        val cafe = categories.create("Кафе", CategoryKind.EXPENSE, color = 0)
        val food = categories.create("Еда", CategoryKind.EXPENSE, color = 0)
        val cash = AccountRepository(phone.accountDao()).create("Нал", rub, AccountKind.CASH, Money(Minor(0), rub), 0)
        val txn = TxnRepository(phone.txnDao(), phone.accountDao())
            .addExpense(cash.id, Money(Minor(300_00), rub), cafe.id, LocalDate.of(2026, 11, 1))
        phoneEngine.enable("phone", primary = true, baseline = true)
        phoneEngine.sync()
        laptopEngine.enable("laptop", primary = false, baseline = false)
        laptopEngine.sync()

        val beforeMerge = BackupRepository(phone).buildPayload()
        categories.mergeInto(cafe.id, food.id)
        phoneEngine.sync()
        laptopEngine.sync()

        BackupRepository(phone).restorePayload(beforeMerge)
        repeat(3) {
            phoneEngine.sync()
            laptopEngine.sync()
        }

        for (db in listOf(phone, laptop)) {
            assertNotNull(db.categoryDao().getById(cafe.id))
            val restored = TxnRepository(db.txnDao(), db.accountDao()).getById(txn.id) as Txn.Expense
            assertEquals(cafe.id, restored.categoryId)
            assertEquals(emptyList<Any>(), BackupRepository.validate(BackupRepository(db).buildPayload()))
        }
        assertEquals(
            phone.categoryDao().observeAll().first().map { it.id }.sorted(),
            laptop.categoryDao().observeAll().first().map { it.id }.sorted(),
        )
        phone.close()
        laptop.close()
    }
}
