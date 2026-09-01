package com.corriente.app.quick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.corriente.app.CorrienteApplication
import com.corriente.app.R
import com.corriente.app.ui.theme.CorrienteTheme
import com.corriente.money.AmountInput
import com.corriente.money.Currency
import com.corriente.money.Money
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * T4.3: быстрый ввод расхода из виджета. Полупрозрачная Activity с цифровой клавиатурой:
 * тап по категории в виджете → сюда → сумма → «✓» → запись в активный счёт → виджет
 * обновляется сам (WidgetUpdater реагирует на запись в БД).
 *
 * Держит ссылку на контейнер через applicationContext — если процесс был убит, его
 * пересоздаст [CorrienteApplication.onCreate] при старте процесса под эту Activity.
 */
class QuickExpenseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID)?.takeIf { it.isNotBlank() }
        val categoryName = intent.getStringExtra(EXTRA_CATEGORY_NAME).orEmpty()
        val container = (applicationContext as CorrienteApplication).container

        setContent {
            CorrienteTheme {
                var currency by remember { mutableStateOf<Currency?>(null) }
                var accountId by remember { mutableStateOf<String?>(null) }
                var accountName by remember { mutableStateOf("") }
                var input by remember { mutableStateOf(AmountInput.empty()) }
                var saving by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    val config = container.widgetConfigStore.config.first()
                    val accounts = container.accountRepository.observeActive().first()
                    val account = accounts.firstOrNull { it.id == config.activeAccountId } ?: accounts.firstOrNull()
                    if (account == null) {
                        finish()
                        return@LaunchedEffect
                    }
                    accountId = account.id
                    accountName = account.name
                    currency = container.currencyRepository.getByCode(account.currency)
                }

                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
                    Surface(
                        tonalElevation = 6.dp,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(categoryName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = accountName.ifEmpty { "…" },
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = input.displayText() + (currency?.let { " ${it.symbol}" } ?: ""),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            val cur = currency
                            QuickKeypad(
                                enabled = cur != null && !saving,
                                onDigit = { if (cur != null) input = input.appendDigit(it, cur) },
                                onPoint = { if (cur != null) input = input.appendDecimalPoint(cur) },
                                onBackspace = { input = input.backspace() },
                            )
                            Button(
                                onClick = {
                                    val c = currency ?: return@Button
                                    val acc = accountId ?: return@Button
                                    val minor = input.toMinorOrNull(c) ?: return@Button
                                    if (minor.raw <= 0L) return@Button
                                    saving = true
                                    scope.launch {
                                        container.txnRepository.addExpense(
                                            acc, Money(minor, c.code), categoryId, LocalDate.now(),
                                        )
                                        finish()
                                    }
                                },
                                enabled = currency != null && !input.isEmpty && !saving,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(androidx.compose.ui.res.stringResource(R.string.save)) }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_CATEGORY_ID = "category_id"
        const val EXTRA_CATEGORY_NAME = "category_name"
    }
}

@Composable
private fun QuickKeypad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onPoint: () -> Unit,
    onBackspace: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf(".", "0", "⌫"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = {
                            when (key) {
                                "." -> onPoint()
                                "⌫" -> onBackspace()
                                else -> onDigit(key[0])
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text(key, style = MaterialTheme.typography.titleLarge) }
                }
            }
        }
    }
}
