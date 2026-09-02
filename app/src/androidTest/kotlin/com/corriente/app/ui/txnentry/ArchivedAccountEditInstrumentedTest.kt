package com.corriente.app.ui.txnentry

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.corriente.app.CorrienteApplication
import com.corriente.app.MainActivity
import com.corriente.app.R
import com.corriente.app.clearMutableTables
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * R5.1 — регрессия `FIX_PLAN` F0.3: правка операции на архивном счёте не должна переносить её
 * на другой счёт. До фикса [TxnEntryViewModel] предлагал только активные счета, поэтому
 * `selectedAccountId` архивной операции падал на первый активный счёт уже на первой же
 * рекомпозиции — заметка сохранялась туда же. Тест открывает такую операцию, проверяет, что
 * архивный счёт показан выбранным, и что после правки заметки строка остаётся на прежнем счёте.
 */
@RunWith(AndroidJUnit4::class)
class ArchivedAccountEditInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val archivedAccountId = "ui-test-archived"
    private val activeAccountId = "ui-test-active"
    private lateinit var txnId: String

    @Before
    fun seed() {
        val container = (composeRule.activity.applicationContext as CorrienteApplication).container
        runBlocking {
            container.database.clearMutableTables()
            val accountDao = container.database.accountDao()
            accountDao.insert(
                AccountEntity(id = archivedAccountId, name = "Старая карта", currencyCode = "RUB", kind = AccountKind.CARD, color = 0),
            )
            accountDao.insert(
                AccountEntity(id = activeAccountId, name = "Активный счёт", currencyCode = "RUB", kind = AccountKind.CASH, color = 0),
            )
            val txns = TxnRepository(container.database.txnDao(), accountDao)
            val created = txns.addExpense(
                accountId = archivedAccountId,
                amount = Money(Minor(500_00), CurrencyCode("RUB")),
                categoryId = null,
                date = LocalDate.now(),
                note = "исходная заметка",
            )
            txnId = created.id
            AccountRepository(accountDao).archive(archivedAccountId)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun editingATransactionOnAnArchivedAccountKeepsItOnThatAccount() {
        composeRule.onNodeWithText("исходная заметка").performClick()
        composeRule.waitForIdle()

        // F0.3: чип архивного счёта показан (с суффиксом «(архив)») и выбран — иначе выбор
        // молча падает на первый активный счёт.
        val archivedLabel = composeRule.activity.getString(R.string.entry_archived_suffix, "Старая карта")
        composeRule.onNodeWithText(archivedLabel).assertExists()

        composeRule.onNodeWithText("исходная заметка").performTextReplacement("новая заметка")
        val saveLabel = composeRule.activity.getString(R.string.save)
        composeRule.onNodeWithText(saveLabel).performClick()
        composeRule.waitForIdle()

        val container = (composeRule.activity.applicationContext as CorrienteApplication).container
        val saved = runBlocking { container.database.txnDao().getById(txnId) }
        assertEquals("правка не должна была перенести операцию на другой счёт", archivedAccountId, saved?.accountId)
        assertEquals("новая заметка", saved?.note)
    }
}
