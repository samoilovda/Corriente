package com.corriente.app.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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

/** R2.3: экран «Бюджеты» из «Настроек» — список + создание/правка/удаление. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    onBack: () -> Unit,
    viewModel: BudgetsViewModel = viewModel(
        factory = with(corrienteContainer()) {
            BudgetsViewModel.factory(budgetRepository, categoryRepository, currencyRepository)
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
                title = { Text(stringResource(R.string.budgets_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::startCreate) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.budgets_add))
            }
        },
    ) { padding ->
        if (state.rows.isEmpty()) {
            Text(
                stringResource(R.string.budgets_empty),
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(state.rows, key = { it.id }) { row ->
                    ListItem(
                        headlineContent = { Text(row.categoryName ?: stringResource(R.string.budgets_whole_currency)) },
                        supportingContent = { Text(stringResource(R.string.budgets_per_month, row.amountText)) },
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
        BudgetEditorDialog(
            editor = editor,
            categories = state.expenseCategories.map { it.id to it.name },
            currencyCodes = state.currencyCodes,
            onCategoryChange = viewModel::setCategory,
            onCurrencyChange = viewModel::setCurrency,
            onAmountChange = viewModel::setAmountText,
            onSave = viewModel::save,
            onDismiss = viewModel::cancelEdit,
        )
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text(stringResource(R.string.budgets_delete_confirm_title)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(id); pendingDeleteId = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetEditorDialog(
    editor: BudgetEditor,
    categories: List<Pair<String, String>>,
    currencyCodes: List<String>,
    onCategoryChange: (String?) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val wholeLabel = stringResource(R.string.budgets_whole_currency)
    val categoryOptions = listOf<String?>(null) + categories.map { it.first }
    val categoryLabel = { id: String? -> id?.let { cid -> categories.firstOrNull { it.first == cid }?.second } ?: wholeLabel }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (editor.editingId == null) R.string.budgets_add else R.string.budgets_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SimpleDropdown(
                    label = stringResource(R.string.txn_entry_category),
                    value = categoryLabel(editor.categoryId),
                    options = categoryOptions.map(categoryLabel),
                    onSelect = { selectedLabel -> onCategoryChange(categoryOptions.firstOrNull { categoryLabel(it) == selectedLabel }) },
                )
                SimpleDropdown(
                    label = stringResource(R.string.currencies_title),
                    value = editor.currencyCode,
                    options = currencyCodes,
                    onSelect = onCurrencyChange,
                )
                OutlinedTextField(
                    value = editor.amountText,
                    onValueChange = onAmountChange,
                    label = { Text(stringResource(R.string.budgets_amount)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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
