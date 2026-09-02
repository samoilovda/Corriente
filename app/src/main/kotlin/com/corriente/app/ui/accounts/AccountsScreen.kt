package com.corriente.app.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.corrienteContainer
import com.corriente.app.ui.common.rememberMessageSnackbarState
import com.corriente.data.db.entity.AccountKind
import com.corriente.money.CurrencyCode
import com.corriente.money.MoneyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel = viewModel(
        factory = with(corrienteContainer()) {
            AccountsViewModel.factory(accountRepository, currencyRepository, accountBalanceUseCase)
        },
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val editor by viewModel.editor.collectAsState()
    val message by viewModel.messages.collectAsState()
    val snackbarState = rememberMessageSnackbarState(message, viewModel::consumeMessage)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_accounts)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::startCreate) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.accounts_add))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxWidth().padding(padding)) {
            if (state.groups.isEmpty() && state.archived.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.accounts_empty),
                        Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            state.groups.forEach { group ->
                item(key = "cur-${group.currency.code.code}") {
                    CurrencyGroupHeader(
                        title = "${group.currency.code.code} · ${group.currency.symbol}",
                        total = group.total?.let { MoneyFormatter.format(it, group.currency) },
                    )
                }
                items(group.rows, key = { it.account.id }) { row ->
                    AccountListRow(
                        title = row.account.name,
                        subtitle = accountSubtitle(row.account.kind, row.account.includeInTotal),
                        trailing = row.balance?.let { MoneyFormatter.format(it, group.currency) }.orEmpty(),
                        onClick = { viewModel.startEdit(row.account) },
                    )
                    HorizontalDivider()
                }
            }
            if (state.archived.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.accounts_archived),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(state.archived, key = { "arch-${it.account.id}" }) { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(row.account.name, Modifier.weight(1f))
                        IconButton(onClick = { viewModel.unarchive(row.account.id) }) {
                            Icon(Icons.Filled.Unarchive, contentDescription = stringResource(R.string.accounts_unarchive))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    editor?.let { current ->
        AccountEditorDialog(
            editor = current,
            activeCurrencies = state.activeCurrencies.map { it.code },
            onDismiss = viewModel::closeEditor,
            onSave = viewModel::save,
            onArchive = { current.editingId?.let(viewModel::archive); viewModel.closeEditor() },
            onDelete = { current.editingId?.let(viewModel::deleteIfUnused); viewModel.closeEditor() },
        )
    }
}

@Composable
private fun CurrencyGroupHeader(title: String, total: String?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        if (total != null) {
            Text(total, style = MaterialTheme.typography.titleSmall)
        }
    }
    HorizontalDivider()
}

@Composable
private fun AccountListRow(title: String, subtitle: String, trailing: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Text(trailing)
    }
}

@Composable
private fun accountSubtitle(kind: AccountKind, includeInTotal: Boolean): String {
    val kindLabel = stringResource(
        when (kind) {
            AccountKind.CASH -> R.string.account_kind_cash
            AccountKind.CARD -> R.string.account_kind_card
            AccountKind.SAVINGS -> R.string.account_kind_savings
            AccountKind.DEBT -> R.string.account_kind_debt
        },
    )
    return if (includeInTotal) kindLabel else "$kindLabel · ${stringResource(R.string.accounts_excluded_from_total)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountEditorDialog(
    editor: AccountEditor,
    activeCurrencies: List<CurrencyCode>,
    onDismiss: () -> Unit,
    onSave: (AccountForm) -> Boolean,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var name by rememberSaveable(editor) { mutableStateOf(editor.name) }
    var currencyCode by rememberSaveable(editor) {
        mutableStateOf((editor.currency ?: activeCurrencies.firstOrNull())?.code.orEmpty())
    }
    var kindName by rememberSaveable(editor) { mutableStateOf(editor.kind.name) }
    var openingBalance by rememberSaveable(editor) { mutableStateOf(editor.openingBalanceText) }
    var includeInTotal by rememberSaveable(editor) { mutableStateOf(editor.includeInTotal) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (editor.editingId == null) R.string.accounts_new else R.string.accounts_edit))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.accounts_name)) },
                    singleLine = true,
                )
                EnumDropdown(
                    label = stringResource(R.string.accounts_currency),
                    value = currencyCode,
                    options = activeCurrencies.map { it.code },
                    enabled = !editor.currencyLocked,
                    onSelect = { currencyCode = it },
                )
                EnumDropdown(
                    label = stringResource(R.string.accounts_kind),
                    value = kindName,
                    options = AccountKind.entries.map { it.name },
                    enabled = true,
                    onSelect = { kindName = it },
                )
                OutlinedTextField(
                    value = openingBalance,
                    onValueChange = { openingBalance = it },
                    label = { Text(stringResource(R.string.accounts_opening_balance)) },
                    enabled = !editor.currencyLocked,
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = includeInTotal, onCheckedChange = { includeInTotal = it })
                    Text(stringResource(R.string.accounts_include_in_total))
                }
                if (editor.editingId != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onArchive) { Text(stringResource(R.string.accounts_archive)) }
                        TextButton(onClick = onDelete) { Text(stringResource(R.string.accounts_delete)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && currencyCode.isNotBlank(),
                onClick = {
                    onSave(
                        AccountForm(
                            name = name,
                            currency = CurrencyCode(currencyCode),
                            kind = AccountKind.valueOf(kindName),
                            openingBalanceText = openingBalance,
                            includeInTotal = includeInTotal,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumDropdown(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
