package com.corriente.app.ui.accountbalance

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.corrienteContainer
import com.corriente.app.ui.report.AccountBalanceLineChart
import com.corriente.app.ui.report.PeriodMode

/** R3.2: «динамика по счёту» — остаток выбранного счёта по дням, график на Canvas (как T5.3). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountBalanceScreen(
    onBack: () -> Unit,
    initialAccountId: String? = null,
    viewModel: AccountBalanceViewModel = viewModel(
        factory = with(corrienteContainer()) {
            AccountBalanceViewModel.factory(accountRepository, txnRepository, currencyRepository, initialAccountId)
        },
    ),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_balance_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp)) {
                state.accounts.forEach { account ->
                    FilterChip(
                        selected = state.selectedAccountId == account.id,
                        onClick = { viewModel.selectAccount(account.id) },
                        label = { Text(account.name) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                PeriodChip(state.periodMode, PeriodMode.MONTH, R.string.report_period_month, viewModel::setPeriodMode)
                PeriodChip(state.periodMode, PeriodMode.QUARTER, R.string.report_period_quarter, viewModel::setPeriodMode)
                PeriodChip(state.periodMode, PeriodMode.YEAR, R.string.report_period_year, viewModel::setPeriodMode)
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                IconButton(onClick = { viewModel.shiftPeriod(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.report_prev))
                }
                Text(state.periodLabel, Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { viewModel.shiftPeriod(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.report_next))
                }
            }

            state.currentBalanceText?.let {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        stringResource(R.string.account_balance_current),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(it, style = MaterialTheme.typography.titleMedium)
                }
            }

            if (state.points.size < 2) {
                Text(
                    stringResource(R.string.account_balance_empty),
                    Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                AccountBalanceLineChart(
                    points = state.points.map { it.valueMinor },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(state.points.first().date.toString(), style = MaterialTheme.typography.labelSmall)
                    Text(state.points.last().date.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PeriodChip(current: PeriodMode, value: PeriodMode, labelRes: Int, onSelect: (PeriodMode) -> Unit) {
    FilterChip(
        selected = current == value,
        onClick = { onSelect(value) },
        label = { Text(stringResource(labelRes)) },
        modifier = Modifier.padding(end = 8.dp),
    )
}
