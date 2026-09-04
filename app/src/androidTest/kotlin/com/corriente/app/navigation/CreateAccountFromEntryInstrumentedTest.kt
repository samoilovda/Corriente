package com.corriente.app.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.corriente.app.CorrienteApplication
import com.corriente.app.MainActivity
import com.corriente.app.R
import com.corriente.app.clearMutableTables
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Регрессия: с пустым списком счетов экран ввода предлагает «Создать счёт»; после перехода
 * оттуда на «Счета» вкладка «Операции» в нижней навигации должна остаться рабочей.
 *
 * Раньше `onCreateAccount` в [CorrienteNavHost] просто клал «Счета» поверх экрана ввода, и
 * первый же тап по «Операциям» через saveState/restoreState восстанавливал экран ввода
 * (под-экран) — нижняя панель пропадала, до вкладки «Операции» было не добраться без
 * перезапуска приложения.
 *
 * [БД]: гоняет настоящую Activity/БД эмулятора (ADR-011) — требует подключённого устройства.
 */
@RunWith(AndroidJUnit4::class)
class CreateAccountFromEntryInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun emptyOutAccounts() {
        val container = (composeRule.activity.applicationContext as CorrienteApplication).container
        runBlocking { container.database.clearMutableTables() }
        composeRule.waitForIdle()
    }

    @Test
    fun transactionsTabStillWorksAfterGoingToCreateAnAccount() {
        // FAB «Добавить расход» → экран ввода с пустым списком счетов.
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.txn_add_expense))
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.txn_entry_create_account))
            .performClick()
        composeRule.waitForIdle()

        // Мы на «Счетах» (пустой список), нижняя навигация видна — виден пункт «Отчёт».
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.accounts_empty)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.nav_report)).assertIsDisplayed()

        // Несколько переключений между разделами: с испорченным back-stack'ом «Операции» после
        // экскурсии за счётом переставали открываться со второго тапа (saveState/restoreState
        // воскрешали сохранённый под-экран ввода вместо корневого раздела).
        repeat(2) {
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.nav_transactions)).performClick()
            composeRule.waitForIdle()
            // Реально на экране «Операции»: виден его пустой список и нижняя панель.
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.txn_list_empty)).assertIsDisplayed()
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.nav_report)).assertIsDisplayed()
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.txn_entry_no_accounts)).assertDoesNotExist()

            composeRule.onNodeWithText(composeRule.activity.getString(R.string.nav_accounts)).performClick()
            composeRule.waitForIdle()
        }
    }
}
