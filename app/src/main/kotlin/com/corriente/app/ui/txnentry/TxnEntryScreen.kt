package com.corriente.app.ui.txnentry

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.ui.common.CategoryGlyph
import com.corriente.app.corrienteContainer
import com.corriente.app.ui.common.rememberMessageSnackbarState
import com.corriente.data.model.Category
import com.corriente.money.CalcOp
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TxnEntryScreen(
    onDone: () -> Unit,
    onCreateAccount: () -> Unit = {},
    editingTxnId: String? = null,
    initialKind: EntryKind = EntryKind.EXPENSE,
    initialCategoryId: String? = null,
    initialAccountId: String? = null,
    repeatTxnId: String? = null,
    viewModel: TxnEntryViewModel = viewModel(
        factory = with(corrienteContainer()) {
            TxnEntryViewModel.factory(
                txnRepository, accountRepository, categoryRepository, currencyRepository, editingTxnId, initialKind,
                initialCategoryId, initialAccountId, repeatTxnId,
            )
        },
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val finished by viewModel.finished.collectAsState()
    val message by viewModel.messages.collectAsState()
    val pendingUndo by viewModel.pendingUndo.collectAsState()
    val snackbarState = rememberMessageSnackbarState(message, viewModel::consumeMessage)
    var datePickerOpen by remember { mutableStateOf(false) }
    var categorySheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(finished) { if (finished) onDone() }

    // R5.3: снекбар «Операция удалена — Отменить» — держит форму на экране, пока не истечёт
    // таймер ViewModel (UNDO_WINDOW_MS) или пользователь не нажмёт «Отменить»; уход с экрана
    // раньше отменяет и эту корутину вместе с ViewModel — отмена уже невозможна.
    val undoMessage = stringResource(R.string.txn_deleted_undo_message)
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(pendingUndo != null) {
        if (pendingUndo == null) return@LaunchedEffect
        val result = snackbarState.showSnackbar(undoMessage, undoLabel, duration = SnackbarDuration.Indefinite)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (viewModel.isEditing) R.string.txn_entry_edit else R.string.txn_entry_title))
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (viewModel.isEditing) {
                        TextButton(onClick = viewModel::deleteEditing, enabled = pendingUndo == null) {
                            Text(stringResource(R.string.txn_entry_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 12.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                SegmentedButton(
                    selected = state.kind == EntryKind.EXPENSE,
                    enabled = !viewModel.isEditing,
                    onClick = { viewModel.setKind(EntryKind.EXPENSE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.category_kind_expense)) }
                SegmentedButton(
                    selected = state.kind == EntryKind.INCOME,
                    enabled = !viewModel.isEditing,
                    onClick = { viewModel.setKind(EntryKind.INCOME) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.category_kind_income)) }
            }

            if (!state.loaded) {
                // F2.5: ещё грузится — не мигаем «нет счетов».
            } else if (state.accounts.isEmpty()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.txn_entry_no_accounts), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onCreateAccount) { Text(stringResource(R.string.txn_entry_create_account)) }
                }
            } else {
                // Дата — по центру, с иконкой календаря (как в Monefy).
                // UI-1.2 / UI-2.7: дата и счёт — чипами рядом («Сегодня», «Кошелёк»).
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AssistChip(
                        onClick = { datePickerOpen = true },
                        label = { Text(com.corriente.app.ui.common.dayLabel(state.date)) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.CalendarMonth,
                                contentDescription = stringResource(R.string.txn_entry_date),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    var accountChipMenuOpen by remember { mutableStateOf(false) }
                    val accountName = state.accounts.firstOrNull { it.id == state.selectedAccountId }?.name
                    if (accountName != null) {
                        Box {
                            AssistChip(
                                onClick = { accountChipMenuOpen = true },
                                label = { Text(accountName, maxLines = 1) },
                                leadingIcon = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                            AccountMenu(
                                expanded = accountChipMenuOpen,
                                onDismiss = { accountChipMenuOpen = false },
                                accounts = state.accounts,
                                selectedAccountId = state.selectedAccountId,
                                onSelect = { viewModel.selectAccount(it); accountChipMenuOpen = false },
                            )
                        }
                    }
                }

                AmountBar(
                    amountText = state.amountText,
                    currencyCode = state.currency?.code?.code.orEmpty(),
                    isIncome = state.kind == EntryKind.INCOME,
                    accounts = state.accounts,
                    selectedAccountId = state.selectedAccountId,
                    onSelectAccount = viewModel::selectAccount,
                    onBackspace = viewModel::pressBackspace,
                )
                if (state.nonPositiveResult || state.calcOverflow) {
                    Text(
                        stringResource(
                            if (state.calcOverflow) R.string.txn_entry_amount_too_large
                            else R.string.txn_entry_amount_must_be_positive,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    TextField(
                        value = state.note,
                        onValueChange = viewModel::setNote,
                        placeholder = { Text(stringResource(R.string.txn_entry_note)) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }

                // R2.2: «частые» — тап заполняет вид/счёт/категорию/сумму целиком.
                if (state.frequentOptions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    ChipRow(stringResource(R.string.txn_entry_frequent)) {
                        state.frequentOptions.forEach { option ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.applyFrequent(option) },
                                label = { Text("${option.label} · ${option.amountText}") },
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))
                Keypad(
                    onDigit = viewModel::pressDigit,
                    onDecimalPoint = viewModel::pressDecimalPoint,
                    onOp = viewModel::pressOp,
                    onEquals = viewModel::pressEquals,
                    equalsEnabled = state.hasPendingCalc,
                )
                Spacer(Modifier.height(8.dp))
                val selectedCategoryName = state.categories.firstOrNull { it.id == state.selectedCategoryId }?.name
                if (initialCategoryId != null && !viewModel.isEditing && selectedCategoryName != null) {
                    // Пришли тапом по категории с главного экрана (как в Monefy): кнопка сразу добавляет операцию
                    // в эту категорию; сменить категорию — кнопкой рядом.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { viewModel.save() },
                            enabled = state.canSave && !state.saving && pendingUndo == null,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) {
                            Text(stringResource(R.string.txn_entry_add_to_category, selectedCategoryName))
                        }
                        OutlinedButton(
                            onClick = { categorySheetOpen = true },
                            enabled = !state.saving && pendingUndo == null,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.height(56.dp),
                        ) { Text(stringResource(R.string.txn_entry_other_category)) }
                    }
                } else {
                    val selectedCategory = state.categories.firstOrNull { it.id == state.selectedCategoryId }
                    if (selectedCategory != null) {
                        // Категория уже выбрана — показываем её (иконка и цвет), а не «Выбрать категорию».
                        FilledTonalButton(
                            onClick = { categorySheetOpen = true },
                            enabled = !state.saving && pendingUndo == null,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            if (!selectedCategory.icon.isNullOrBlank()) {
                                val tint = if (selectedCategory.color != 0) Color(selectedCategory.color) else MaterialTheme.colorScheme.onSecondaryContainer
                                CategoryGlyph(selectedCategory.icon, selectedCategory.name, tint, size = 22.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(selectedCategory.name)
                        }
                    } else {
                        Button(
                            onClick = { categorySheetOpen = true },
                            enabled = !state.saving && pendingUndo == null,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            Text(stringResource(R.string.txn_entry_choose_category))
                        }
                    }
                }
                if (viewModel.isEditing) {
                    Button(
                        onClick = { viewModel.save() },
                        enabled = state.canSave && !state.saving && pendingUndo == null,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text(stringResource(R.string.save)) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Выбор категории (как в Monefy): при создании выбор сразу сохраняет операцию, при правке — только выбирает.
    if (categorySheetOpen) {
        ModalBottomSheet(onDismissRequest = { categorySheetOpen = false }) {
            Box(Modifier.padding(horizontal = 12.dp).padding(bottom = 24.dp)) {
                CategoryGrid(
                    label = stringResource(R.string.txn_entry_category),
                    categories = state.categories,
                    selectedId = state.selectedCategoryId,
                    onSelect = { id ->
                        categorySheetOpen = false
                        if (viewModel.isEditing) viewModel.selectCategory(id) else viewModel.selectCategoryAndSave(id)
                    },
                )
            }
        }
    }

    if (datePickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        viewModel.selectDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    datePickerOpen = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

/** F2.6: категории — сетка «иконка + подпись + цвет» (4 колонки), а не горизонтальная лента. */
@Composable
private fun CategoryGrid(
    label: String,
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    val archivedFmt = stringResource(R.string.entry_archived_suffix, "%s")
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            item(key = "none") {
                CategoryCell(
                    text = stringResource(R.string.txn_entry_no_category),
                    icon = null,
                    color = 0,
                    selected = selectedId == null,
                    onClick = { onSelect(null) },
                )
            }
            items(categories, key = { it.id }) { category ->
                CategoryCell(
                    text = if (category.isArchived) archivedFmt.format(category.name) else category.name,
                    icon = category.icon?.takeIf { it.isNotBlank() },
                    color = category.color,
                    selected = selectedId == category.id,
                    onClick = { onSelect(category.id) },
                )
            }
        }
    }
}

@Composable
private fun CategoryCell(
    text: String,
    icon: String?,
    color: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val tint = if (color != 0) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            Modifier.size(32.dp).background(tint.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) CategoryGlyph(icon, text, tint, size = 18.dp)
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChipRow(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun AccountMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    accounts: List<AccountOption>,
    selectedAccountId: String?,
    onSelect: (String) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        accounts.forEach { option ->
            val label = if (option.isArchived) {
                stringResource(R.string.entry_archived_suffix, option.name)
            } else {
                option.name
            }
            DropdownMenuItem(
                text = { Text("$label · ${option.currency.code.code}") },
                onClick = { onSelect(option.id) },
                trailingIcon = if (option.id == selectedAccountId) {
                    { Icon(Icons.Outlined.Check, contentDescription = null) }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * Плашка суммы: слева «таблетка» счёта с кодом валюты (выбор счёта), справа сумма и «⌫».
 * Цвета — из темы (Material You): расход — основной контейнер, доход — третичный.
 */
@Composable
private fun AmountBar(
    amountText: String,
    currencyCode: String,
    isIncome: Boolean,
    accounts: List<AccountOption>,
    selectedAccountId: String?,
    onSelectAccount: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (isIncome) colors.tertiaryContainer else colors.primaryContainer,
        contentColor = if (isIncome) colors.onTertiaryContainer else colors.onPrimaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().height(88.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
        ) {
            Box {
                Surface(
                    onClick = { menuOpen = true },
                    shape = CircleShape,
                    color = LocalContentColor.current.copy(alpha = 0.12f),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AccountBalanceWallet,
                            contentDescription = stringResource(R.string.txn_entry_account),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(currencyCode, style = MaterialTheme.typography.labelLarge)
                    }
                }
                AccountMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    accounts = accounts,
                    selectedAccountId = selectedAccountId,
                    onSelect = { onSelectAccount(it); menuOpen = false },
                )
            }
            // UI-3.1: короткий кросс-фейд при вводе цифры.
            AnimatedContent(
                targetState = amountText,
                transitionSpec = { fadeIn(tween(90)) togetherWith fadeOut(tween(90)) },
                contentAlignment = Alignment.CenterEnd,
                label = "amount-text",
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            ) { shown ->
                Text(
                    text = shown,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            IconButton(onClick = onBackspace) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.txn_entry_backspace))
            }
        }
    }
}

@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onDecimalPoint: () -> Unit,
    onOp: (CalcOp) -> Unit,
    onEquals: () -> Unit,
    equalsEnabled: Boolean,
) {
    // Раскладка как в Monefy: цифры слева, операции — правой колонкой; «=» в нижней строке.
    val rows = listOf(
        listOf("1", "2", "3", "+"),
        listOf("4", "5", "6", "−"),
        listOf("7", "8", "9", "×"),
        listOf(".", "0", "=", "÷"),
    )
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    val isOp = key in setOf("+", "−", "×", "÷")
                    if (key == "=" && !equalsEnabled) {
                        // UI-2.7: «=» появляется, только когда есть что вычислять; место остаётся пустым.
                        Spacer(Modifier.weight(1f).height(64.dp))
                    } else {
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                when (key) {
                                    "." -> onDecimalPoint()
                                    "=" -> onEquals()
                                    "+" -> onOp(CalcOp.PLUS)
                                    "−" -> onOp(CalcOp.MINUS)
                                    "×" -> onOp(CalcOp.TIMES)
                                    "÷" -> onOp(CalcOp.DIVIDE)
                                    else -> onDigit(key[0])
                                }
                            },
                            shape = MaterialTheme.shapes.large,
                            colors = when {
                                key == "=" -> ButtonDefaults.buttonColors()
                                isOp -> ButtonDefaults.filledTonalButtonColors()
                                else -> ButtonDefaults.filledTonalButtonColors(
                                    containerColor = colors.surfaceContainerHigh,
                                    contentColor = colors.onSurface,
                                )
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.weight(1f).height(64.dp),
                        ) {
                            Text(key, fontSize = 28.sp, fontWeight = if (isOp) FontWeight.Medium else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}
