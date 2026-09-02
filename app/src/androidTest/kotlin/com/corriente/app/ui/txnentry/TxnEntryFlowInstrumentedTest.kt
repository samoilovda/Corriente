package com.corriente.app.ui.txnentry

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.corriente.app.CorrienteApplication
import com.corriente.app.MainActivity
import com.corriente.app.R
import com.corriente.app.clearMutableTables
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R5.1: сценарий целиком — FAB «Записать расход» → выбор категории → ввод суммы по клавиатуре
 * → «Сохранить» → строка появляется в списке операций. Падает, если сломается маршрутизация
 * FAB на [com.corriente.app.navigation.CorrienteNavHost], сборка суммы в [AmountInput], или
 * запись через `TxnRepository.addExpense`.
 *
 * [БД]: держит настоящую Activity/БД устройства (ручной DI-контейнер — ADR-011, подменить
 * репозитории тестовыми нечем), поэтому требует подключённого устройства/эмулятора.
 */
@RunWith(AndroidJUnit4::class)
class TxnEntryFlowInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun seed() {
        val container = (composeRule.activity.applicationContext as CorrienteApplication).container
        runBlocking {
            container.database.clearMutableTables()
            container.database.accountDao().insert(
                AccountEntity(
                    id = "ui-test-cash", name = "Наличные-тест", currencyCode = "RUB",
                    kind = AccountKind.CASH, color = 0,
                ),
            )
            container.database.categoryDao().insert(
                CategoryEntity(id = "ui-test-food", name = "Еда-тест", kind = CategoryKind.EXPENSE, color = 0),
            )
        }
        composeRule.waitForIdle()
    }

    @Test
    fun enteringAnExpenseThroughTheFabSavesItAndItAppearsInTheList() {
        val expenseCd = composeRule.activity.getString(R.string.txn_add_expense)
        composeRule.onNodeWithContentDescription(expenseCd).performClick()

        // Экран ввода: категория «Еда-тест», сумма 12.50, «Сохранить».
        composeRule.onNodeWithText("Еда-тест").performClick()
        composeRule.onNodeWithText("1").performClick()
        composeRule.onNodeWithText("2").performClick()
        composeRule.onNodeWithText(".").performClick()
        composeRule.onNodeWithText("5").performClick()
        composeRule.onNodeWithText("0").performClick()

        val saveLabel = composeRule.activity.getString(R.string.save)
        composeRule.onNodeWithText(saveLabel).performClick()
        composeRule.waitForIdle()

        // Назад в списке операций — строка с категорией и суммой должна быть видна.
        composeRule.onNodeWithText("Еда-тест").assertExists()
        // MoneyFormatter (I-25): знак «-», точка десятичная, независимо от локали устройства.
        // Сумма встречается и в строке операции, и в подытоге дня (единственный расход за день) —
        // поэтому onAllNodesWithText + первый, а не onNodeWithText (тот требует ровно один узел).
        composeRule.onAllNodesWithText("-12.50 ₽").onFirst().assertExists()
    }
}
