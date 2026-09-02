package com.corriente.app.ui.report

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.corrienteContainer
import com.corriente.data.usecase.ReportKind
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onEditTransaction: (String) -> Unit,
    onOpenFxReport: () -> Unit,
    viewModel: ReportViewModel = viewModel(
        factory = with(corrienteContainer()) {
            ReportViewModel.factory(txnRepository, categoryRepository, currencyRepository, budgetRepository)
        },
    ),
) {
    val state by viewModel.uiState.collectAsState()
    var rangePickerOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // R3.3: экспорт текущего отчёта в CSV через SAF — тот же паттерн, что и экспорт бэкапа
    // на «Настройках» (ActivityResultContracts.CreateDocument), суммы — MoneyFormatter (I-25).
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(viewModel.exportCsv().toByteArray(Charsets.UTF_8))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_report)) },
                actions = {
                    TextButton(onClick = { exportCsvLauncher.launch("corriente-report.csv") }) {
                        Text(stringResource(R.string.report_export_csv))
                    }
                    TextButton(onClick = onOpenFxReport) { Text(stringResource(R.string.report_open_fx)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableChips {
                FilterChip(
                    selected = state.kind == ReportKind.EXPENSE,
                    onClick = { viewModel.setKind(ReportKind.EXPENSE) },
                    label = { Text(stringResource(R.string.category_kind_expense)) },
                )
                FilterChip(
                    selected = state.kind == ReportKind.INCOME,
                    onClick = { viewModel.setKind(ReportKind.INCOME) },
                    label = { Text(stringResource(R.string.category_kind_income)) },
                )
            }

            ScrollableChips {
                PeriodModeChip(state.periodMode, PeriodMode.MONTH, R.string.report_period_month, viewModel::setPeriodMode)
                PeriodModeChip(state.periodMode, PeriodMode.QUARTER, R.string.report_period_quarter, viewModel::setPeriodMode)
                PeriodModeChip(state.periodMode, PeriodMode.YEAR, R.string.report_period_year, viewModel::setPeriodMode)
                FilterChip(
                    selected = state.periodMode == PeriodMode.CUSTOM,
                    onClick = { rangePickerOpen = true },
                    label = { Text(stringResource(R.string.report_period_custom)) },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                IconButton(onClick = { viewModel.shiftPeriod(-1) }, enabled = state.periodMode != PeriodMode.CUSTOM) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.report_prev))
                }
                Text(state.periodLabel, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { viewModel.shiftPeriod(1) }, enabled = state.periodMode != PeriodMode.CUSTOM) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.report_next))
                }
            }

            if (state.currencyCodes.size > 1) {
                ScrollableChips {
                    state.currencyCodes.forEach { code ->
                        FilterChip(
                            selected = state.selectedCurrency == code,
                            onClick = { viewModel.selectCurrency(code) },
                            label = { Text(code) },
                        )
                    }
                }
            }

            state.totalText?.let {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.report_total), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(it, style = MaterialTheme.typography.titleMedium)
                }
            }
            // R2.3: бюджет «на всё» — общая полоса над списком категорий.
            state.wholeCurrencyBudgetBar?.let { bar ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    BudgetProgressBar(bar)
                }
            }
            HorizontalDivider()

            if (state.rows.isEmpty()) {
                Text(
                    stringResource(R.string.report_empty),
                    Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (state.monthly.any { it.valueMinor > 0 }) {
                        item(key = "chart-monthly") { MonthlyBarChart(state.monthly) }
                    }
                    if (state.slices.any { it.valueMinor > 0 }) {
                        item(key = "chart-structure") { CategoryDonut(state.slices) }
                        item(key = "chart-divider") { HorizontalDivider() }
                    }
                    items(state.rows, key = { it.categoryId ?: "none" }) { row ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { viewModel.openDrilldown(row.categoryId) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(row.name ?: stringResource(R.string.report_no_category), Modifier.weight(1f))
                                Text("${row.sharePercent}%", style = MaterialTheme.typography.bodySmall)
                                Text(row.amountText)
                            }
                            // R3.1: изменение к тому же периоду раньше — «+18 % к прошлому месяцу»,
                            // прочерк вместо 0 % при отсутствии данных за прошлый период.
                            row.changeText?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // R2.3: полоса «потрачено из бюджета» — только когда бюджет задан.
                            row.categoryId?.let { state.categoryBudgetBars[it] }?.let { bar -> BudgetProgressBar(bar) }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (rangePickerOpen) {
        val rangeState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { rangePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = rangeState.selectedStartDateMillis
                    val end = rangeState.selectedEndDateMillis
                    if (start != null && end != null) {
                        viewModel.setCustomRange(
                            Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate(),
                            Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate(),
                        )
                    }
                    rangePickerOpen = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { rangePickerOpen = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DateRangePicker(state = rangeState, modifier = Modifier.weight(1f)) }
    }

    state.drilldown?.let { drilldown ->
        ModalBottomSheet(onDismissRequest = viewModel::closeDrilldown) {
            Text(
                drilldown.categoryName ?: stringResource(R.string.report_no_category),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            LazyColumn(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                items(drilldown.txns, key = { it.id }) { brief ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { viewModel.closeDrilldown(); onEditTransaction(brief.id) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(brief.date.toString())
                            brief.note?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Text(brief.amountText)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ScrollableChips(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun PeriodModeChip(current: PeriodMode, mode: PeriodMode, labelRes: Int, onSelect: (PeriodMode) -> Unit) {
    FilterChip(selected = current == mode, onClick = { onSelect(mode) }, label = { Text(stringResource(labelRes)) })
}

/** R2.3: полоса «потрачено из бюджета» — цвет меняется на error при перерасходе. */
@Composable
private fun BudgetProgressBar(bar: BudgetBar) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        LinearProgressIndicator(
            // Float — только на этой границе с Compose (InvariantGuards floatAllowlist);
            // ViewModel считает и хранит целые проценты (I-1).
            progress = { bar.percentClamped / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = if (bar.isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${bar.spentText} / ${bar.budgetText}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (bar.isOverBudget) {
                Text(
                    stringResource(R.string.report_budget_over),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
