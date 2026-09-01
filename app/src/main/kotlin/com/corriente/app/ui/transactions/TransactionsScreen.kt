package com.corriente.app.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.corrienteContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onAddTransfer: () -> Unit,
    onEditTransaction: (String) -> Unit,
    onEditTransfer: (String) -> Unit,
    viewModel: TransactionsViewModel = viewModel(
        factory = with(corrienteContainer()) {
            TransactionsViewModel.factory(txnRepository, accountRepository, categoryRepository, currencyRepository)
        },
    ),
) {
    val state by viewModel.uiState.collectAsState()
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_transactions)) },
                actions = {
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(badge = { if (state.filter.isActive) Badge() }) {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = stringResource(R.string.txn_filters),
                            )
                        }
                    }
                    IconButton(onClick = onAddTransfer) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.transfer_title))
                    }
                },
            )
        },
        floatingActionButton = {
            // «+» доход слева, «−» расход справа (у thumb-зоны): расход — самый частый сценарий,
            // основная кнопка; «+» вторичного цвета.
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FloatingActionButton(
                    onClick = onAddIncome,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    elevation = FloatingActionButtonDefaults.loweredElevation(),
                ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.txn_add_income)) }
                FloatingActionButton(onClick = onAddExpense) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.txn_add_expense))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = viewModel::setQuery,
                singleLine = true,
                label = { Text(stringResource(R.string.txn_search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            FilterBar(
                accountLabel = state.accounts.firstOrNull { it.id == state.filter.accountId }?.name
                    ?: stringResource(R.string.txn_list_all_accounts),
                accountOptions = state.accounts.map { it.id to it.name },
                onAccount = viewModel::setAccountFilter,
                currencyLabel = state.filter.currencyCode ?: stringResource(R.string.txn_list_all_currencies),
                currencyOptions = state.currencyCodes,
                onCurrency = viewModel::setCurrencyFilter,
            )
            HorizontalDivider()

            if (state.sections.isEmpty()) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        stringResource(if (state.noMatch) R.string.txn_list_no_match else R.string.txn_list_empty),
                        Modifier.padding(top = 48.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    state.sections.forEach { section ->
                        item(key = "h-${section.date}") { DayHeader(section) }
                        items(section.rows, key = { it.id }) { row ->
                            TxnRowItem(
                                row,
                                onClick = {
                                    when {
                                        row.isTransfer -> onEditTransfer(row.id)
                                        row.editable -> onEditTransaction(row.id)
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            state = state,
            onCategory = viewModel::setCategoryFilter,
            onPeriod = viewModel::setPeriod,
            onAmountRange = viewModel::setAmountRange,
            onClear = viewModel::clearFilters,
            onDismiss = { showFilters = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    state: TransactionsUiState,
    onCategory: (String?) -> Unit,
    onPeriod: (java.time.LocalDate?, java.time.LocalDate?) -> Unit,
    onAmountRange: (Long?, Long?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.txn_filters), style = MaterialTheme.typography.titleMedium)

            FilterDropdown(
                label = state.categories.firstOrNull { it.id == state.filter.categoryId }?.name
                    ?: stringResource(R.string.txn_filter_all_categories),
                allLabel = stringResource(R.string.txn_filter_all_categories),
                options = state.categories.map { it.id to it.name },
                onSelect = onCategory,
            )

            var from by remember(state.filter.from) { mutableStateOf(state.filter.from?.toString() ?: "") }
            var to by remember(state.filter.to) { mutableStateOf(state.filter.to?.toString() ?: "") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = from, onValueChange = { from = it }, singleLine = true, modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.txn_filter_from)) }, placeholder = { Text("2026-01-01") },
                )
                OutlinedTextField(
                    value = to, onValueChange = { to = it }, singleLine = true, modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.txn_filter_to)) }, placeholder = { Text("2026-12-31") },
                )
            }
            TextButton(onClick = {
                onPeriod(parseDateOrNull(from), parseDateOrNull(to))
            }) { Text(stringResource(R.string.txn_filter_apply_period)) }

            var min by remember(state.filter.minAmountMinor) {
                mutableStateOf(state.filter.minAmountMinor?.let { (it / 100).toString() } ?: "")
            }
            var max by remember(state.filter.maxAmountMinor) {
                mutableStateOf(state.filter.maxAmountMinor?.let { (it / 100).toString() } ?: "")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = min, onValueChange = { min = it.filter(Char::isDigit) }, singleLine = true,
                    modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.txn_filter_amount_min)) },
                )
                OutlinedTextField(
                    value = max, onValueChange = { max = it.filter(Char::isDigit) }, singleLine = true,
                    modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.txn_filter_amount_max)) },
                )
            }
            TextButton(onClick = {
                onAmountRange(
                    min.toLongOrNull()?.let { it * 100 },
                    max.toLongOrNull()?.let { it * 100 },
                )
            }) { Text(stringResource(R.string.txn_filter_apply_amount)) }

            HorizontalDivider()
            TextButton(onClick = { onClear(); onDismiss() }) {
                Text(stringResource(R.string.txn_filter_clear))
            }
        }
    }
}

private fun parseDateOrNull(text: String): java.time.LocalDate? =
    runCatching { java.time.LocalDate.parse(text.trim()) }.getOrNull()

@Composable
private fun FilterBar(
    accountLabel: String,
    accountOptions: List<Pair<String, String>>,
    onAccount: (String?) -> Unit,
    currencyLabel: String,
    currencyOptions: List<String>,
    onCurrency: (String?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterDropdown(accountLabel, stringResource(R.string.txn_list_all_accounts), accountOptions, onAccount)
        FilterDropdown(
            currencyLabel,
            stringResource(R.string.txn_list_all_currencies),
            currencyOptions.map { it to it },
            onCurrency,
        )
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    allLabel: String,
    options: List<Pair<String, String>>,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = true }) { Text(label) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(allLabel) }, onClick = { onSelect(null); expanded = false })
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun DayHeader(section: DaySection) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(section.date.toString(), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Column(horizontalAlignment = Alignment.End) {
            section.totals.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun TxnRowItem(row: TxnRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!row.isTransfer) {
            val dotColor = if (row.color != 0) {
                androidx.compose.ui.graphics.Color(row.color)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(28.dp)
                    .background(dotColor, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                row.icon?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(row.title)
            row.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(row.amountText)
    }
}
