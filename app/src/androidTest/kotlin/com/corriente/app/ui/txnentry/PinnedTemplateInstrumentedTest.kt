package com.corriente.app.ui.txnentry

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.corriente.app.CorrienteApplication
import com.corriente.app.MainActivity
import com.corriente.app.R
import com.corriente.app.UiPrefs
import com.corriente.app.clearMutableTables
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
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

/** E1: долгое нажатие по шаблону в «Частых» закрепляет его; закрепление хранится на устройстве. */
@RunWith(AndroidJUnit4::class)
class PinnedTemplateInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val chipText = "Кофе-тест · Наличные-тест · 150.00 ₽"

    @Before
    fun seed() {
        val container = (composeRule.activity.applicationContext as CorrienteApplication).container
        runBlocking {
            container.database.clearMutableTables()
            val accountDao = container.database.accountDao()
            accountDao.insert(
                AccountEntity(id = "pin-cash", name = "Наличные-тест", currencyCode = "RUB", kind = AccountKind.CASH, color = 0),
            )
            container.database.categoryDao().insert(
                CategoryEntity(id = "pin-coffee", name = "Кофе-тест", kind = CategoryKind.EXPENSE, color = 0),
            )
            val txns = TxnRepository(container.database.txnDao(), accountDao)
            repeat(2) {
                txns.addExpense("pin-cash", Money(Minor(150_00), CurrencyCode("RUB")), "pin-coffee", LocalDate.now().minusDays(it.toLong()))
            }
        }
        UiPrefs.pinnedEntries(composeRule.activity).pinned.value.forEach { UiPrefs.pinnedEntries(composeRule.activity).toggle(it) }
        composeRule.waitForIdle()
    }

    @Test
    fun longPressPinsTemplateAndTapStillFillsTheForm() {
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.txn_add_expense)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(chipText).performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertEquals(1, UiPrefs.pinnedEntries(composeRule.activity).pinned.value.size)

        // Тап по закреплённому шаблону по-прежнему заполняет форму, а не снимает закрепление.
        composeRule.onNodeWithText(chipText).performClick()
        composeRule.waitForIdle()
        assertEquals(1, UiPrefs.pinnedEntries(composeRule.activity).pinned.value.size)

        composeRule.onNodeWithText(chipText).performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertEquals(0, UiPrefs.pinnedEntries(composeRule.activity).pinned.value.size)
    }
}
