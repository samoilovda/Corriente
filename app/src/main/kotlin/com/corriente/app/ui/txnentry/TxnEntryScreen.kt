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
    val snackbarState = rememberMessageSnackbarState(message, viewModel::consumeMessage)
    var datePickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(finished) { if (finished) onDone() }

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
                        TextButton(onClick = viewModel::deleteEditing) {
                            Text(stringResource(R.string.txn_entry_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.txn_entry_no_accounts),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = onCreateAccount) { Text(stringResource(R.string.txn_entry_create_account)) }
                    }
                } else {
                    Text(
                        text = "${state.amountText} ${state.currency?.symbol.orEmpty()}",
                        style = MaterialTheme.typography.displaySmall,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                    if (state.nonPositiveResult) {
                        Text(
                            stringResource(R.string.txn_entry_amount_must_be_positive),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    ChipRow(stringResource(R.string.txn_entry_account)) {
                        state.accounts.forEach { option ->
                            val label = if (option.isArchived) {
                                stringResource(R.string.entry_archived_suffix, option.name)
                            } else {
                                option.name
                            }
                            FilterChip(
                                selected = state.selectedAccountId == option.id,
                                onClick = { viewModel.selectAccount(option.id) },
                                label = { Text(label) },
                            )
                        }
                    }

                    CategoryGrid(
                        label = stringResource(R.string.txn_entry_category),
                        categories = state.categories,
                        selectedId = state.selectedCategoryId,
                        onSelect = viewModel::selectCategory,
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.txn_entry_date), Modifier.weight(1f))
                        TextButton(onClick = { datePickerOpen = true }) { Text(state.date.toString()) }
                    }

                    OutlinedTextField(
                        value = state.note,
                        onValueChange = viewModel::setNote,
                        label = { Text(stringResource(R.string.txn_entry_note)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (state.accounts.isNotEmpty()) {
                // R2.2: «частые» — тап заполняет вид/счёт/категорию/сумму целиком.
                if (state.frequentOptions.isNotEmpty()) {
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
                Spacer(Modifier.height(8.dp))
                Keypad(
                    onDigit = viewModel::pressDigit,
                    onDecimalPoint = viewModel::pressDecimalPoint,
                    onBackspace = viewModel::pressBackspace,
                    onPlus = { viewModel.pressOp(com.corriente.money.CalcOp.PLUS) },
                    onMinus = { viewModel.pressOp(com.corriente.money.CalcOp.MINUS) },
                    onEquals = viewModel::pressEquals,
                    equalsEnabled = state.hasPendingCalc,
                )
                Button(
                    onClick = { viewModel.save() },
                    enabled = state.canSave,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) { Text(stringResource(R.string.save)) }
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

@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onDecimalPoint: () -> Unit,
    onBackspace: () -> Unit,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
    onEquals: () -> Unit,
    equalsEnabled: Boolean,
) {
    // T5.5: 4-я колонка — калькулятор (сложение/вычитание сумм одной валюты).
    val rows = listOf(
        listOf("7", "8", "9", "−"),
        listOf("4", "5", "6", "+"),
        listOf("1", "2", "3", "="),
        listOf(".", "0", "⌫"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = {
                            when (key) {
                                "." -> onDecimalPoint()
                                "⌫" -> onBackspace()
                                "+" -> onPlus()
                                "−" -> onMinus()
                                "=" -> onEquals()
                                else -> onDigit(key[0])
                            }
                        },
                        enabled = key != "=" || equalsEnabled,
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        if (key == "⌫") {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = stringResource(R.string.txn_entry_backspace))
                        } else {
                            Text(key, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                if (row.size < 4) Spacer(Modifier.weight(1f))
            }
        }
    }
}
