package com.corriente.app.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.Unarchive
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.corrienteContainer
import com.corriente.app.ui.categories.CategoryPalette
import com.corriente.app.ui.common.BackupReminderBanner
import com.corriente.app.ui.common.rememberMessageSnackbarState
import com.corriente.data.db.entity.AccountKind
import com.corriente.money.CurrencyCode
import com.corriente.money.MoneyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onOpenAutoBackup: () -> Unit = {},
    onOpenAccountBalance: (String) -> Unit = {},
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
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.accounts_add))
            }
        },
    ) { padding ->
        var archivedExpanded by remember { mutableStateOf(false) }
        LazyColumn(Modifier.fillMaxWidth().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            // R1.5: плашка «Последний бэкап: N дней назад» — та же логика, что и на «Настройках».
            item { BackupReminderBanner(onClick = onOpenAutoBackup) }
            if (state.groups.isEmpty() && state.archived.isEmpty()) {
                item {
                    com.corriente.app.ui.common.EmptyState(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        title = stringResource(R.string.accounts_empty_title),
                        body = stringResource(R.string.accounts_empty),
                    )
                }
            }
            // UI-2.5: итог по каждой валюте — отдельная карточка сверху (I-8: валюты не складываются).
            val cards = currencyCards(state.groups)
            if (cards.isNotEmpty()) {
                item(key = "cards") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    ) {
                        items(cards, key = { it.code }) { card -> CurrencyTotalCard(card) }
                    }
                }
            }
            state.groups.forEach { group ->
                item(key = "cur-${group.currency.code.code}") {
                    CurrencyGroupHeader("${group.currency.code.code} · ${group.currency.symbol}")
                }
                itemsIndexed(group.rows, key = { _, it -> it.account.id }) { index, row ->
                    AccountListRow(
                        title = row.account.name,
                        subtitle = accountSubtitle(row.account.kind, row.account.includeInTotal),
                        trailing = row.balance?.let { MoneyFormatter.format(it, group.currency) }.orEmpty(),
                        trailingNegative = row.balance?.isNegative == true,
                        color = row.account.color,
                        icon = row.account.icon,
                        kind = row.account.kind,
                        shape = groupItemShape(index, group.rows.size),
                        onClick = { viewModel.startEdit(row.account) },
                        onOpenBalanceChart = { onOpenAccountBalance(row.account.id) },
                    )
                }
            }
            if (state.archived.isNotEmpty()) {
                // Архивные — свёрнутой группой внизу.
                item(key = "archived-header") {
                    Row(
                        Modifier.fillMaxWidth().clickable { archivedExpanded = !archivedExpanded }
                            .padding(start = 28.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.accounts_archived) + " · ${state.archived.size}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (archivedExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                        )
                    }
                }
                if (archivedExpanded) {
                    itemsIndexed(state.archived, key = { _, it -> "arch-${it.account.id}" }) { index, row ->
                        Surface(
                            shape = groupItemShape(index, state.archived.size),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(row.account.name, Modifier.weight(1f))
                                IconButton(onClick = { viewModel.unarchive(row.account.id) }) {
                                    Icon(Icons.Outlined.Unarchive, contentDescription = stringResource(R.string.accounts_unarchive))
                                }
                            }
                        }
                    }
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
            onDelete = { current.editingId?.let(viewModel::deleteIfUnused) },
        )
    }
}

@Composable
private fun CurrencyGroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Карточка итога по одной валюте. */
@Composable
private fun CurrencyTotalCard(card: CurrencyCard) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text("${card.code} · ${card.symbol}", style = MaterialTheme.typography.labelLarge)
            card.total?.let { total ->
                com.corriente.app.ui.common.MoneyText(
                    MoneyFormatter.format(total, card.currency),
                    com.corriente.app.ui.common.MoneyKind.BALANCE,
                    isNegativeBalance = total.isNegative,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (total.isNegative) com.corriente.app.ui.theme.LocalCorrienteColors.current.expense else androidx.compose.ui.graphics.Color.Unspecified,
                )
            }
        }
    }
}

/** Скругление строки внутри группы: внешние углы крупные, внутренние мелкие — группа читается как одна плитка. */
@Composable
private fun groupItemShape(index: Int, size: Int): androidx.compose.ui.graphics.Shape {
    val outer = 24.dp
    val inner = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == size - 1) outer else inner,
        bottomEnd = if (index == size - 1) outer else inner,
    )
}

private fun kindIcon(kind: AccountKind): ImageVector = when (kind) {
    AccountKind.CASH -> Icons.Outlined.Payments
    AccountKind.CARD -> Icons.Outlined.CreditCard
    AccountKind.SAVINGS -> Icons.Outlined.Savings
    AccountKind.DEBT -> Icons.Outlined.RequestQuote
}

@Composable
private fun AccountListRow(
    title: String,
    subtitle: String,
    trailing: String,
    trailingNegative: Boolean,
    color: Int,
    icon: String?,
    kind: AccountKind,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    onOpenBalanceChart: () -> Unit,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Цветной круг счёта с иконкой (своя или по типу счёта) — не «пустой кружок», похожий на радиокнопку.
            Box(
                Modifier.size(40.dp).background(if (color != 0) Color(color) else MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val tint = if (color != 0) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                if (!icon.isNullOrBlank()) {
                    com.corriente.app.ui.common.CategoryGlyph(icon, title, tint, size = 22.dp)
                } else {
                    Icon(kindIcon(kind), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            com.corriente.app.ui.common.MoneyText(
                trailing, com.corriente.app.ui.common.MoneyKind.BALANCE, isNegativeBalance = trailingNegative,
            )
            // R3.2: график остатка счёта по дням — отдельный тап, не конфликтует с открытием
            // редактора счёта по тапу на всю строку.
            IconButton(onClick = onOpenBalanceChart) {
                Icon(Icons.Outlined.ShowChart, contentDescription = stringResource(R.string.account_balance_chart))
            }
        }
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
    var color by rememberSaveable(editor) { mutableIntStateOf(editor.color) }
    var icon by rememberSaveable(editor) { mutableStateOf(editor.icon) }
    var confirmDelete by rememberSaveable(editor) { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.accounts_delete_confirm, editor.name)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.accounts_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

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
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text(stringResource(R.string.accounts_icon)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.accounts_color), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryPalette.forEach { swatch ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .background(Color(swatch), CircleShape)
                                .border(
                                    width = if (swatch == color) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { color = swatch },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = includeInTotal, onCheckedChange = { includeInTotal = it })
                    Text(stringResource(R.string.accounts_include_in_total))
                }
                if (editor.editingId != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onArchive) { Text(stringResource(R.string.accounts_archive)) }
                        // A2.2: «Удалить» — только для неиспользуемого счёта; у используемого — подсказка.
                        if (!editor.currencyLocked) {
                            TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.accounts_delete)) }
                        }
                    }
                    if (editor.currencyLocked) {
                        Text(
                            stringResource(R.string.delete_blocked_account_transactions),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                            color = color,
                            icon = icon,
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
