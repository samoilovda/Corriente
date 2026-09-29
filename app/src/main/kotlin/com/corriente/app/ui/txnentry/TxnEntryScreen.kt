package com.corriente.app.ui.txnentry

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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
    viewModel: TxnEntryViewModel = viewModel(
        factory = with(corrienteContainer()) {
            TxnEntryViewModel.factory(
                txnRepository, accountRepository, categoryRepository, currencyRepository, editingTxnId, initialKind,
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
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                FilterChip(
                    selected = state.kind == EntryKind.EXPENSE,
                    enabled = !viewModel.isEditing,
                    onClick = { viewModel.setKind(EntryKind.EXPENSE) },
                    label = { Text(stringResource(R.string.category_kind_expense)) },
                )
                FilterChip(
                    selected = state.kind == EntryKind.INCOME,
                    enabled = !viewModel.isEditing,
                    onClick = { viewModel.setKind(EntryKind.INCOME) },
                    label = { Text(stringResource(R.string.category_kind_income)) },
                )
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
                Row(
                    Modifier.fillMaxWidth().clickable { datePickerOpen = true }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.txn_entry_date))
                    Spacer(Modifier.width(8.dp))
                    Text(state.date.toString(), style = MaterialTheme.typography.titleMedium)
                }

                AmountBar(
                    amountText = state.amountText,
                    currencyCode = state.currency?.code?.code.orEmpty(),
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
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                OutlinedButton(
                    onClick = { categorySheetOpen = true },
                    enabled = !state.saving && pendingUndo == null,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Text((selectedCategoryName ?: stringResource(R.string.txn_entry_choose_category)).uppercase())
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
        Box(
            Modifier
                .size(20.dp)
                .background(
                    if (color != 0) Color(color) else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) Text(icon, style = MaterialTheme.typography.bodySmall)
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

/** Зелёная плашка суммы (как в Monefy): слева кошелёк с кодом валюты (выбор счёта), в центре сумма, справа «⌫». */
@Composable
private fun AmountBar(
    amountText: String,
    currencyCode: String,
    accounts: List<AccountOption>,
    selectedAccountId: String?,
    onSelectAccount: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        color = Color(0xFF7BC896),
        contentColor = Color.White,
        shape = RoundedCornerShape(4.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().height(88.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Column(
                    Modifier.clickable { menuOpen = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.AccountBalanceWallet, contentDescription = stringResource(R.string.txn_entry_account))
                    Text(currencyCode, style = MaterialTheme.typography.labelMedium)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    accounts.forEach { option ->
                        val label = if (option.isArchived) {
                            stringResource(R.string.entry_archived_suffix, option.name)
                        } else {
                            option.name
                        }
                        DropdownMenuItem(
                            text = { Text("$label · ${option.currency.code.code}") },
                            onClick = { onSelectAccount(option.id); menuOpen = false },
                            trailingIcon = if (option.id == selectedAccountId) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            Box(Modifier.width(1.dp).height(56.dp).background(Color.White.copy(alpha = 0.6f)))
            Text(
                text = amountText,
                fontSize = 40.sp,
                fontWeight = FontWeight.Light,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = {
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
                        enabled = key != "=" || equalsEnabled,
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.weight(1f).height(64.dp),
                    ) {
                        Text(key, fontSize = 30.sp, fontWeight = FontWeight.Light)
                    }
                }
            }
        }
    }
}
