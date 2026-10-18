package com.corriente.app.ui.transactions

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** U07/U11: планирование достижимо с главного экрана; пустая база предлагает необязательный старт. */
@RunWith(AndroidJUnit4::class)
class PlanningEntryInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val container get() = (composeRule.activity.applicationContext as CorrienteApplication).container
    private fun string(id: Int) = composeRule.activity.getString(id)

    @Test
    fun budgetsAreTwoStepsFromTheMainScreen() {
        runBlocking {
            container.database.clearMutableTables()
            container.database.accountDao().insert(
                AccountEntity(id = "ui-plan-cash", name = "План-тест", currencyCode = "RUB", kind = AccountKind.CASH, color = 0),
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(string(R.string.more_actions)).performClick() // 1
        composeRule.onNodeWithText(string(R.string.budgets_title)).performClick() // 2
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(string(R.string.budgets_empty_title)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun emptyDatabaseShowsAnOptionalStartCardThatCanBeSkipped() {
        runBlocking { container.database.clearMutableTables() }
        composeRule.runOnUiThread { com.corriente.app.UiPrefs.resetStartCard(composeRule.activity) }
        val start = string(R.string.start_title)
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithText(start).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(string(R.string.start_create_account)).assertExists()
        composeRule.onNodeWithText(string(R.string.start_import_monefy)).assertExists()
        composeRule.onNodeWithText(string(R.string.start_restore)).assertExists()
        composeRule.onNodeWithText(string(R.string.start_skip)).performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(start).fetchSemanticsNodes().isEmpty() }
    }
}
