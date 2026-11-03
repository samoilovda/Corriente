package com.corriente.data.desktop

import com.corriente.data.backup.BackupRepository
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.recurrence.RecurrenceRule
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.RecurrenceRepository
import com.corriente.data.sync.FileSyncFolder
import com.corriente.data.sync.SyncEngine
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

/**
 * W17: на одном устройстве к пустому счёту добавили повторяющееся правило, на другом (ещё не зная о нём) сменили
 * валюту счёта. Смена валюты отклоняется, как для счёта с операциями: правило в рублях на долларовом счёте нарушило бы
 * I-15, и оба устройства застряли бы на ошибке целостности.
 */
class CurrencyChangeWithRecurrenceSyncTest {

    @get:Rule val tmp = TemporaryFolder()
    private val rub = CurrencyCode("RUB")
    private val usd = CurrencyCode("USD")

    @Test
    fun currencyChangeOfAccountWithRecurrenceIsRefused() = runBlocking {
        val folder = FileSyncFolder(tmp.newFolder("sync"))
        val phone = DesktopDatabase.open(tmp.newFile("phone.db").also { it.delete() })
        val laptop = DesktopDatabase.open(tmp.newFile("laptop.db").also { it.delete() })
        val phoneEngine = SyncEngine(phone, folder)
        val laptopEngine = SyncEngine(laptop, folder)
        val account = AccountRepository(phone.accountDao()).create("Карта", rub, AccountKind.CARD, Money(Minor(0), rub), 0)
        phoneEngine.enable("phone", primary = true, baseline = true)
        phoneEngine.sync()
        laptopEngine.enable("laptop", primary = false, baseline = false)
        laptopEngine.sync()

        RecurrenceRepository(phone.recurrenceDao(), phone.txnDao()).create(
            TxnKind.EXPENSE, account.id, null, Money(Minor(500_00), rub), "аренда",
            RecurrenceRule.DayOfMonth(5), LocalDate.of(2026, 12, 1),
        )
        AccountRepository(laptop.accountDao()).setCurrencyAndOpeningBalanceBeforeFirstUse(account.id, usd, Money(Minor(0), usd))

        laptopEngine.sync()
        phoneEngine.sync()
        repeat(3) {
            laptopEngine.sync()
            phoneEngine.sync()
        }

        for (db in listOf(phone, laptop)) {
            assertEquals("RUB", db.accountDao().getById(account.id)!!.currencyCode)
            assertEquals(1, db.recurrenceDao().getAll().size)
            assertEquals(emptyList<Any>(), BackupRepository.validate(BackupRepository(db).buildPayload()))
        }
        phone.close()
        laptop.close()
    }
}
