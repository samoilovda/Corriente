package com.corriente.app.quick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.CorrienteApplication
import com.corriente.app.R
import com.corriente.app.corrienteContainer
import com.corriente.app.ui.theme.CorrienteTheme
import com.corriente.app.ui.txnentry.EntryKind
import kotlinx.coroutines.flow.first

/**
 * R4.2 (было T4.3): быстрый ввод расхода/дохода из виджета. Полупрозрачная Activity с цифровой
 * клавиатурой: тап по категории в виджете → сюда → переключатель расход/доход, выбор счёта,
 * сумма → «✓» → запись → виджет обновляется сам (WidgetUpdater реагирует на запись в БД).
 *
 * Счёт, выбранный здесь, применяется только к этой операции — активный счёт виджета не меняется
 * (см. [QuickEntryViewModel]). Держит ссылку на контейнер через applicationContext — если процесс
 * был убит, его пересоздаст [CorrienteApplication.onCreate] при старте процесса под эту Activity.
 */
class QuickExpenseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID)?.takeIf { it.isNotBlank() }
        val categoryName = intent.getStringExtra(EXTRA_CATEGORY_NAME).orEmpty()
        val container = (applicationContext as CorrienteApplication).container

        setContent {
            CorrienteTheme {
                // Активный счёт виджета читаем один раз как стартовое значение — дальше выбор
                // счёта в этом окне живёт только в ViewModel и не пишется обратно в конфиг.
                var loading by remember { mutableStateOf(true) }
                var initialAccountId by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    val config = container.widgetConfigStore.config.first()
                    val accounts = container.accountRepository.observeActive().first()
                    val resolved = accounts.firstOrNull { it.id == config.activeAccountId } ?: accounts.firstOrNull()
                    if (resolved == null) {
                        finish()
                        return@LaunchedEffect
                    }
                    initialAccountId = resolved.id
                    loading = false
                }

                if (!loading) {
                    QuickEntryContent(
                        categoryId = categoryId,
                        categoryName = categoryName,
                        initialAccountId = initialAccountId,
                        onDone = ::finish,
                    )
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
private fun QuickEntryContent(
    categoryId: String?,
    categoryName: String,
    initialAccountId: String?,
    onDone: () -> Unit,
    viewModel: QuickEntryViewModel = viewModel(
        factory = with(corrienteContainer()) {
            QuickEntryViewModel.factory(
                txnRepository, accountRepository, currencyRepository, categoryId, categoryName, initialAccountId,
            )
        },
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val finished by viewModel.finished.collectAsState()
    val message by viewModel.messages.collectAsState()

    LaunchedEffect(finished) { if (finished) onDone() }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(
            tonalElevation = 6.dp,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.kind == EntryKind.EXPENSE,
                        onClick = { viewModel.setKind(EntryKind.EXPENSE) },
                        label = { Text(stringResource(R.string.category_kind_expense)) },
                    )
                    FilterChip(
                        selected = state.kind == EntryKind.INCOME,
                        onClick = { viewModel.setKind(EntryKind.INCOME) },
                        label = { Text(stringResource(R.string.category_kind_income)) },
                    )
                }
                Text(
                    categoryName.ifEmpty { stringResource(R.string.quick_expense_no_category) },
                    style = MaterialTheme.typography.titleMedium,
                )

                if (state.accounts.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.accounts.forEach { option ->
                            FilterChip(
                                selected = state.selectedAccountId == option.id,
                                onClick = { viewModel.selectAccount(option.id) },
                                label = { Text(option.name) },
                            )
                        }
                    }
                } else {
                    Text(
                        text = state.selectedAccount?.name.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }

                Text(
                    text = state.amount.displayText() + (state.currency?.let { " ${it.symbol}" } ?: ""),
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (message != null) {
                    Text(
                        stringResource(R.string.quick_expense_save_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                val cur = state.currency
                QuickKeypad(
                    enabled = cur != null && !state.saving,
                    onDigit = viewModel::pressDigit,
                    onPoint = viewModel::pressDecimalPoint,
                    onBackspace = viewModel::pressBackspace,
                )
                Button(
                    onClick = { viewModel.save() },
                    enabled = state.canSave && !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
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
