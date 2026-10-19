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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.corriente.app.ui.common.groupItemShape
import com.corriente.data.db.entity.AccountKind
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import com.corriente.money.MoneyFormatter
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onOpenAutoBackup: () -> Unit = {},
    onOpenAccountBalance: (String) -> Unit = {},
    viewModel: AccountsViewModel = viewModel(
        factory = with(corrienteContainer()) {
            AccountsViewModel.factory(accountRepository, currencyRepository, accountBalanceUseCase, txnRepository)
        },
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val editor by viewModel.editor.collectAsState()
    val adjust by viewModel.adjust.collectAsState()
    val message by viewModel.messages.collectAsState()
    val snackbarState = rememberMessageSnackbarState(message, viewModel::consumeMessage)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
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
                        modifier = Modifier.animateItem(),
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
                            .padding(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
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

    adjust?.let { target ->
        AdjustBalanceDialog(target = target, onDismiss = viewModel::closeAdjust, onSave = viewModel::saveAdjust)
    }

    editor?.let { current ->
        AccountEditorDialog(
            editor = current,
            activeCurrencies = state.activeCurrencies.map { it.code },
            onDismiss = viewModel::closeEditor,
            onSave = viewModel::save,
            onArchive = { current.editingId?.let(viewModel::archive); viewModel.closeEditor() },
            onAdjust = { current.editingId?.let { id -> viewModel.closeEditor(); viewModel.startAdjust(id) } },
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
        modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** Карточка итога по одной валюте. */
@Composable
private fun CurrencyTotalCard(card: CurrencyCard) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("${card.code} · ${card.symbol}", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            card.total?.let { total ->
                com.corriente.app.ui.common.MoneyText(
                    text = MoneyFormatter.format(total, card.currency),
                    kind = com.corriente.app.ui.common.MoneyKind.BALANCE,
                    isNegativeBalance = total.isNegative,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (total.isNegative) com.corriente.app.ui.theme.LocalCorrienteColors.current.expense else MaterialTheme.colorScheme.onPrimaryContainer,
                    deemphasizeFraction = true,
                )
            }
        }
    }
}


private fun kindIcon(kind: AccountKind): ImageVector = when (kind) {
    AccountKind.CASH -> Icons.Outlined.Payments
    AccountKind.CARD -> Icons.Outlined.CreditCard
    AccountKind.SAVINGS -> Icons.Outlined.Savings
    AccountKind.DEBT -> Icons.Outlined.RequestQuote
}

@Composable
private fun AccountListRow(
    modifier: Modifier = Modifier,
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Цветной бейдж счёта с иконкой (своя или по типу счёта) — не «пустой кружок», похожий на радиокнопку.
            Box(
                Modifier.size(40.dp).background(if (color != 0) Color(color) else MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(14.dp)),
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
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            com.corriente.app.ui.common.MoneyText(
                trailing,
                com.corriente.app.ui.common.MoneyKind.BALANCE,
                isNegativeBalance = trailingNegative,
                style = MaterialTheme.typography.titleMedium,
                deemphasizeFraction = true,
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
    onAdjust: () -> Unit,
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
                        TextButton(onClick = onAdjust) { Text(stringResource(R.string.adjust_action)) }
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

/**
 * Корректировка баланса: пользователь вводит реальный остаток, приложение само считает разницу с расчётным и пишет её
 * отдельной записью — не доход и не расход, мимо категорий, отчётов и бюджетов.
 */
@Composable
private fun AdjustBalanceDialog(target: AdjustTarget, onDismiss: () -> Unit, onSave: (String) -> Boolean) {
    val currency = target.currency
    // Поле открывается с выделенным текущим остатком — набранное число заменяет его, а не вклеивается внутрь.
    val initialText = remember(target.account.id) { openingBalanceText(target.balance.amount, currency) }
    var field by rememberSaveable(target.account.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length)))
    }
    val text = field.text
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val actual = remember(text) { text.takeIf { it.isNotBlank() }?.let { openingBalanceMinor(it, currency) } }
    val delta = actual?.let { Money(Minor(it.raw - target.balance.amount.raw), currency.code) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adjust_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.adjust_body, target.account.name, MoneyFormatter.format(target.balance, currency)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    label = { Text(stringResource(R.string.adjust_actual)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (delta != null) {
                    Text(
                        if (delta.amount.raw == 0L) {
                            stringResource(R.string.adjust_no_difference)
                        } else {
                            stringResource(
                                R.string.adjust_difference,
                                (if (delta.isNegative) "" else "+") + MoneyFormatter.format(delta, currency),
                            )
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Text(
                    stringResource(R.string.adjust_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = actual != null && delta?.amount?.raw != 0L, onClick = { onSave(text) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
