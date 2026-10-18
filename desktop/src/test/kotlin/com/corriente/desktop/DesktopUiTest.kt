package com.corriente.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.model.Txn
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

/** UI-тесты окна для компьютера (T5): DesktopContainer на временной папке данных, настоящая БД и репозитории. */
@OptIn(ExperimentalTestApi::class)
class DesktopUiTest {
    @get:Rule val rule = createComposeRule()
    private val dir = Files.createTempDirectory("corriente-ui").toFile()
    private val c = DesktopContainer(dir)

    @After fun tearDown() {
        c.database.close()
        dir.deleteRecursively()
    }

    private fun show() = rule.setContent { MaterialTheme { Surface { App(c) } } }

    private fun account(currency: String = "RUB", name: String = "Наличные") = runBlocking {
        c.accounts.create(name, CurrencyCode(currency), AccountKind.CASH, Money(Minor(0), CurrencyCode(currency)), 0xFF66BB6A.toInt())
    }

    private fun txns(): List<Txn> = runBlocking { c.txns.observeAll().first() }

    private fun categoryId(name: String) = runBlocking { c.categories.observeActive().first().first { it.name == name }.id }

    private fun openExpense() {
        rule.onNodeWithText("−  Расход").performClick()
        rule.waitUntilExists("amount")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.waitUntilExists(tag: String) =
        waitUntil(5_000) { onAllNodesWithTagCount(tag) > 0 }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(SemanticsMatcher("tag=$tag") { it.config.getOrNull(SemanticsProperties.TestTag) == tag }).fetchSemanticsNodes().size

    private fun waitForTxns(n: Int) = rule.waitUntil(5_000) { txns().size == n }

    @Test fun emptyDatabaseShowsHint() {
        show()
        rule.onNodeWithText("Счетов пока нет", substring = true).assertIsDisplayed()
    }

    @Test fun addExpenseWithEnterAppearsInListAndBalance() {
        account()
        show()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("−  Расход").fetchSemanticsNodes().isNotEmpty() }
        openExpense()
        rule.onNodeWithTag("amount").performTextInput("150,5")
        rule.onNodeWithTag("cat-${categoryId("Продукты")}").performClick()
        rule.onNodeWithTag("amount").performKeyInput { pressKey(Key.Enter) }
        waitForTxns(1)
        val t = txns().single() as Txn.Expense
        assertEquals(15050L, t.amount.amount.raw)
        assertEquals(categoryId("Продукты"), t.categoryId)
        rule.waitForIdle()
        assertEquals("Enter не должен сохранять дважды", 1, txns().size)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Продукты").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("150", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun addExpenseWithSaveButton() {
        account()
        show()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("−  Расход").fetchSemanticsNodes().isNotEmpty() }
        openExpense()
        rule.onNodeWithTag("amount").performTextInput("42")
        rule.onNodeWithText("Сохранить").performClick()
        waitForTxns(1)
        assertEquals(4200L, (txns().single() as Txn.Expense).amount.amount.raw)
    }

    @Test fun editAmountAndDelete() {
        val acc = account()
        runBlocking { c.txns.addExpense(acc.id, Money(Minor(1000), acc.currency), null, java.time.LocalDate.now(), "обед") }
        show()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("обед", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("обед", substring = true).onFirst().performClick()
        rule.waitUntilExists("amount")
        rule.onNodeWithTag("amount").performTextClearance()
        rule.onNodeWithTag("amount").performTextInput("25")
        rule.onNodeWithText("Сохранить").performClick()
        rule.waitUntil(5_000) { (txns().singleOrNull() as? Txn.Expense)?.amount?.amount?.raw == 2500L }

        rule.waitUntil(5_000) { rule.onAllNodesWithText("обед", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText("обед", substring = true).onFirst().performClick()
        rule.waitUntilExists("amount")
        rule.onNodeWithText("Удалить").performClick()
        waitForTxns(0)
    }

    @Test fun ringIconOpensEntryWithCategoryPreselected() {
        val acc = account()
        val groceries = categoryId("Продукты")
        runBlocking { c.txns.addExpense(acc.id, Money(Minor(5000), acc.currency), groceries, java.time.LocalDate.now(), null) }
        show()
        rule.waitUntil(5_000) { rule.onAllNodes(SemanticsMatcher("ring") { n -> n.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("ring-") == true }).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("ring-$groceries").performClick()
        rule.waitUntilExists("amount")
        rule.onNodeWithTag("amount").performTextInput("10")
        rule.onNodeWithText("Сохранить").performClick()
        rule.waitUntil(5_000) { txns().size == 2 }
        assertTrue(txns().all { (it as Txn.Expense).categoryId == groceries })
    }

    @Test fun invalidInputShowsErrorAndWritesNothing() {
        account()
        show()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("−  Расход").fetchSemanticsNodes().isNotEmpty() }
        openExpense()
        rule.onNodeWithText("Сохранить").performClick() // пусто
        rule.onNodeWithText("Введите сумму больше нуля").assertIsDisplayed()
        rule.onNodeWithTag("amount").performTextInput("0")
        rule.onNodeWithText("Сохранить").performClick()
        rule.onNodeWithText("Введите сумму больше нуля").assertIsDisplayed()
        rule.onNodeWithTag("amount").performTextClearance()
        rule.onNodeWithTag("amount").performTextInput("5")
        rule.onNodeWithTag("date").performTextClearance()
        rule.onNodeWithTag("date").performTextInput("14.10.2026")
        rule.onNodeWithText("Сохранить").performClick()
        rule.onNodeWithText("Дата в формате ГГГГ-ММ-ДД").assertIsDisplayed()
        assertTrue(txns().isEmpty())
    }

    @Test fun switchingToCurrencyWithoutMinorUnitsTrimsAmount() {
        account("RUB", "Рубли")
        account("JPY", "Иены")
        show()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("−  Расход").fetchSemanticsNodes().isNotEmpty() }
        openExpense()
        rule.onNodeWithTag("account-picker").performClick()
        rule.onAllNodesWithText("Рубли · RUB").onLast().performClick()
        rule.onNodeWithTag("amount").performTextInput("12,5")
        rule.onNodeWithTag("account-picker").performClick()
        rule.onAllNodesWithText("Иены · JPY").onLast().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Сохранить").performClick()
        waitForTxns(1)
        assertEquals(12L, (txns().single() as Txn.Expense).amount.amount.raw)
    }

    // U13: поиск — по всей истории и по той же области, что на Android (заметка/категория/счёт).
    @Test fun searchFindsOperationsOutsideTheShownMonth() {
        val id = account().id
        runBlocking {
            c.txns.addExpense(id, Money(Minor(300_00), CurrencyCode("RUB")), null, java.time.LocalDate.now().minusMonths(4), "старый чек")
        }
        show()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("−  Расход").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("старый чек", substring = true).assertDoesNotExist()
        rule.onNodeWithTag("search").performTextInput("СТАРЫЙ")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("старый чек", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Найдено во всей истории", substring = true).assertIsDisplayed()
        // Esc в поле поиска очищает запрос.
        rule.onNodeWithTag("search").performKeyInput { pressKey(Key.Escape) }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("старый чек", substring = true).fetchSemanticsNodes().isEmpty() }
    }

    // U13: Esc на изменённой форме спрашивает; «Продолжить ввод» сохраняет набранное, «Сбросить» ничего не пишет.
    @Test fun escapeOnDirtyFormAsksAndDiscardWritesNothing() {
        account()
        show()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("−  Расход").fetchSemanticsNodes().isNotEmpty() }
        openExpense()
        rule.onNodeWithTag("amount").performTextInput("5")
        rule.onNodeWithTag("amount").performKeyInput { pressKey(Key.Escape) }
        rule.onNodeWithText("Закрыть без сохранения?").assertIsDisplayed()
        rule.onNodeWithText("Продолжить ввод").performClick()
        rule.waitUntilExists("amount")
        rule.onNodeWithTag("amount").assertTextContains("5")
        rule.onNodeWithText("Отмена").performClick()
        rule.onNodeWithText("Сбросить").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTagCount("amount") == 0 }
        assertTrue(txns().isEmpty())
    }

    // U01/U13: лишний знак после запятой — ошибка, а не тихое обрезание; Enter её не обходит.
    @Test fun excessPrecisionIsAnErrorAndEnterDoesNotSave() {
        account()
        show()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("−  Расход").fetchSemanticsNodes().isNotEmpty() }
        openExpense()
        rule.onNodeWithTag("amount").performTextInput("12.505")
        rule.onNodeWithTag("amount").performKeyInput { pressKey(Key.Enter) }
        rule.onNodeWithText("У RUB не больше 2 знаков после запятой").assertIsDisplayed()
        assertTrue(txns().isEmpty())
    }

    // U13: два Enter подряд — одна операция.
    @Test fun doubleEnterWritesOneTransaction() {
        account()
        show()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("−  Расход").fetchSemanticsNodes().isNotEmpty() }
        openExpense()
        rule.onNodeWithTag("amount").performTextInput("7")
        rule.onNodeWithTag("amount").performKeyInput { pressKey(Key.Enter); pressKey(Key.Enter) }
        waitForTxns(1)
        rule.waitForIdle()
        assertEquals(1, txns().size)
    }
}
