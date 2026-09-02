package com.corriente.app.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.corrienteContainer
import com.corriente.app.ui.common.rememberMessageSnackbarState
import com.corriente.data.db.entity.RecurrenceRuleType
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.recurrence.RecurrenceRule

/** R2.4: экран «Повторяющиеся» из «Настроек» — список + создание/правка/удаление правил. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    viewModel: RecurringViewModel = viewModel(
        factory = with(corrienteContainer()) {
            RecurringViewModel.factory(recurrenceRepository, accountRepository, categoryRepository, currencyRepository)
        },
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.messages.collectAsState()
    val snackbarState = rememberMessageSnackbarState(message, viewModel::consumeMessage)
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recurring_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::startCreate) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.recurring_add))
            }
        },
    ) { padding ->
        if (state.rows.isEmpty()) {
            Text(
                stringResource(R.string.recurring_empty),
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(state.rows, key = { it.id }) { row ->
                    ListItem(
                        headlineContent = { Text(recurringRowTitle(row)) },
                        supportingContent = {
                            Column {
                                Text("${row.amountText} — ${recurringRuleText(row.rule)}")
                                Text(
                                    stringResource(R.string.recurring_next_run, row.nextRunText),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { pendingDeleteId = row.id }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                            }
                        },
                        modifier = Modifier.clickable { viewModel.startEdit(row.id) },
                    )
                }
            }
        }
    }

    state.editor?.let { editor ->
        RecurringEditorDialog(
            editor = editor,
            accounts = state.accounts.map { it.id to it.name },
            categories = categoriesFor(editor.kind, state.categories).map { it.id to it.name },
            onKindChange = viewModel::setKind,
            onAccountChange = viewModel::setAccount,
            onCategoryChange = viewModel::setCategory,
            onAmountChange = viewModel::setAmountText,
            onNoteChange = viewModel::setNote,
            onRuleTypeChange = viewModel::setRuleType,
            onDayOfMonthChange = viewModel::setDayOfMonthText,
            onIntervalDaysChange = viewModel::setIntervalDaysText,
            onSave = viewModel::save,
            onDismiss = viewModel::cancelEdit,
        )
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.recurring_delete_confirm_title)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(id); pendingDeleteId = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * Заголовок строки списка (R6.3): вид и «без категории» — UI-текст из ресурсов, имя счёта/
 * категории — данные пользователя, не локализуются.
 */
@Composable
private fun recurringRowTitle(row: RecurringRow): String {
    val kindLabel = stringResource(
        if (row.kind == TxnKind.INCOME) R.string.recurring_kind_income else R.string.recurring_kind_expense,
    )
    val categoryLabel = row.categoryName ?: stringResource(R.string.txn_entry_no_category)
    val accountLabel = row.accountName ?: "?"
    return "$kindLabel: $categoryLabel · $accountLabel"
}

@Composable
private fun recurringRuleText(rule: RecurrenceRule): String = when (rule) {
    is RecurrenceRule.DayOfMonth -> stringResource(R.string.recurring_row_rule_day_of_month, rule.day)
    is RecurrenceRule.EveryNDays -> stringResource(R.string.recurring_row_rule_every_n_days, rule.intervalDays)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringEditorDialog(
    editor: RecurringEditor,
    accounts: List<Pair<String, String>>,
    categories: List<Pair<String, String>>,
    onKindChange: (TxnKind) -> Unit,
    onAccountChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onRuleTypeChange: (RecurrenceRuleType) -> Unit,
    onDayOfMonthChange: (String) -> Unit,
    onIntervalDaysChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val noCategoryLabel = stringResource(R.string.txn_entry_no_category)
    val categoryOptions = listOf<String?>(null) + categories.map { it.first }
    val categoryLabel = { id: String? -> id?.let { cid -> categories.firstOrNull { it.first == cid }?.second } ?: noCategoryLabel }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (editor.editingId == null) R.string.recurring_add else R.string.recurring_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = editor.kind == TxnKind.EXPENSE,
                        enabled = editor.editingId == null,
                        onClick = { onKindChange(TxnKind.EXPENSE) },
                        label = { Text(stringResource(R.string.recurring_kind_expense)) },
                    )
                    FilterChip(
                        selected = editor.kind == TxnKind.INCOME,
                        enabled = editor.editingId == null,
                        onClick = { onKindChange(TxnKind.INCOME) },
                        label = { Text(stringResource(R.string.recurring_kind_income)) },
                    )
                }
                SimpleDropdown(
                    label = stringResource(R.string.recurring_account),
                    value = accounts.firstOrNull { it.first == editor.accountId }?.second.orEmpty(),
                    options = accounts.map { it.second },
                    onSelect = { label -> accounts.firstOrNull { it.second == label }?.let { onAccountChange(it.first) } },
                )
                SimpleDropdown(
                    label = stringResource(R.string.recurring_category),
                    value = categoryLabel(editor.categoryId),
                    options = categoryOptions.map(categoryLabel),
                    onSelect = { label -> onCategoryChange(categoryOptions.firstOrNull { categoryLabel(it) == label }) },
                )
                OutlinedTextField(
                    value = editor.amountText,
                    onValueChange = onAmountChange,
                    label = { Text(stringResource(R.string.recurring_amount)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editor.note,
                    onValueChange = onNoteChange,
                    label = { Text(stringResource(R.string.txn_entry_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = editor.ruleType == RecurrenceRuleType.DAY_OF_MONTH,
                        onClick = { onRuleTypeChange(RecurrenceRuleType.DAY_OF_MONTH) },
                        label = { Text(stringResource(R.string.recurring_rule_day_of_month)) },
                    )
                    FilterChip(
                        selected = editor.ruleType == RecurrenceRuleType.EVERY_N_DAYS,
                        onClick = { onRuleTypeChange(RecurrenceRuleType.EVERY_N_DAYS) },
                        label = { Text(stringResource(R.string.recurring_rule_every_n_days)) },
                    )
                }
                if (editor.ruleType == RecurrenceRuleType.DAY_OF_MONTH) {
                    OutlinedTextField(
                        value = editor.dayOfMonthText,
                        onValueChange = onDayOfMonthChange,
                        label = { Text(stringResource(R.string.recurring_day_of_month)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = editor.intervalDaysText,
                        onValueChange = onIntervalDaysChange,
                        label = { Text(stringResource(R.string.recurring_interval_days)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}
