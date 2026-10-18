package com.corriente.app.ui.transactions

import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.corriente.app.CorrienteApplication
import com.corriente.app.MainActivity
import com.corriente.app.R
import com.corriente.app.clearMutableTables
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * U01/U04: невалидный порог суммы не применяется — список остаётся прежним, ошибка видна у поля;
 * исправленный порог в точности валюты (RUB, 2 знака) фильтрует список.
 */
@RunWith(AndroidJUnit4::class)
class TxnFilterSheetInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seed() {
        val container = (composeRule.activity.applicationContext as CorrienteApplication).container
        runBlocking {
            container.database.clearMutableTables()
            container.database.accountDao().insert(
                AccountEntity(id = "ui-filter-cash", name = "Кошелёк-тест", currencyCode = "RUB", kind = AccountKind.CASH, color = 0),
            )
            val rub = CurrencyCode("RUB")
            container.txnRepository.addExpense("ui-filter-cash", Money(Minor(12_50), rub), null, LocalDate.now(), "мелочь")
            container.txnRepository.addExpense("ui-filter-cash", Money(Minor(200_00), rub), null, LocalDate.now(), "крупно")
        }
        composeRule.waitForIdle()
    }

    private fun string(id: Int, vararg args: Any) = composeRule.activity.getString(id, *args)

    private fun assertRowShown(note: String) {
        // Вертикальный список операций — единственный узел с ScrollToIndex (горизонтальные ряды чипов его не имеют).
        composeRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(note))
        composeRule.onAllNodesWithText(note).onFirst().assertExists()
    }

    private fun openSheetForAccount() {
        composeRule.onNodeWithContentDescription(string(R.string.txn_filters)).performClick()
        // Без счёта/валюты сумма недоступна — объяснение на экране.
        composeRule.onNodeWithText(string(R.string.txn_filter_amount_no_currency)).performScrollTo().assertExists()
        composeRule.onNodeWithText(string(R.string.txn_list_all_accounts)).performScrollTo().performClick()
        composeRule.onNodeWithText("Кошелёк-тест").performClick()
        composeRule.onNodeWithText(string(R.string.txn_filter_amount_in, "RUB")).assertExists()
    }

    private fun minField() = composeRule.onNode(hasText(string(R.string.txn_filter_amount_min)) and hasSetTextAction())

    @Test
    fun invalidAmountKeepsTheListAndValidAmountFiltersIt() {
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText("крупно").fetchSemanticsNodes().isNotEmpty() }
        openSheetForAccount()
        minField().performScrollTo().performTextInput("125.505")
        composeRule.onNodeWithText(string(R.string.txn_filter_apply)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.txn_filter_error_amount_decimals, "RUB", 2)).assertExists()
        composeRule.onNodeWithText(string(R.string.txn_filter_not_applied)).assertExists()

        // Лист закрыт без применения — в списке по-прежнему обе операции, фильтр не активен.
        // Первое «Назад» может лишь спрятать клавиатуру — жмём, пока лист не закроется.
        repeat(3) {
            if (composeRule.onAllNodesWithText(string(R.string.txn_filter_apply)).fetchSemanticsNodes().isNotEmpty()) {
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                composeRule.waitForIdle()
            }
        }
        assertRowShown("мелочь")
        assertRowShown("крупно")

        openSheetForAccount()
        minField().performScrollTo().performTextInput("100,5")
        composeRule.onNodeWithText(string(R.string.txn_filter_apply)).performScrollTo().performClick()
        composeRule.waitForIdle()

        assertRowShown("крупно")
        composeRule.onNodeWithText("мелочь").assertDoesNotExist()
        // Порог виден над списком chip-ом с валютой.
        composeRule.onNodeWithText(string(R.string.txn_filter_chip_amount_min, "100.50\u00A0₽"), useUnmergedTree = true)
            .assertExists()
    }
}
