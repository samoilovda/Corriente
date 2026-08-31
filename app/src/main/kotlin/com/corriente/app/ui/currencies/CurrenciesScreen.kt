package com.corriente.app.ui.currencies

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.corriente.data.model.ManagedCurrency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrenciesScreen(
    onBack: () -> Unit,
    viewModel: CurrenciesViewModel = viewModel(
        factory = CurrenciesViewModel.factory(corrienteContainer().currencyRepository),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<ManagedCurrency?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.currencies_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.currencies_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.currencies, key = { it.code.code }) { currency ->
                    CurrencyRow(
                        currency = currency,
                        onToggle = { active -> viewModel.setActive(currency.code, active) },
                        onEdit = { editing = currency },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    editing?.let { currency ->
        CurrencyEditDialog(
            currency = currency,
            onDismiss = { editing = null },
            onConfirm = { symbol, displayScale ->
                viewModel.updateDisplay(currency.code, symbol, displayScale, currency.minorUnits)
                editing = null
            },
        )
    }
}

@Composable
private fun CurrencyRow(
    currency: ManagedCurrency,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("${currency.code.code}  ${currency.symbol}")
            Text(
                text = currency.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = currency.isActive, onCheckedChange = onToggle)
    }
}

@Composable
private fun CurrencyEditDialog(
    currency: ManagedCurrency,
    onDismiss: () -> Unit,
    onConfirm: (symbol: String, displayScale: Int) -> Unit,
) {
    var symbol by remember(currency) { mutableStateOf(currency.symbol) }
    var displayScale by remember(currency) { mutableStateOf(currency.displayScale) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(currency.code.code) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    label = { Text(stringResource(R.string.currencies_symbol)) },
                    singleLine = true,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.currencies_display_scale))
                    TextButton(onClick = { displayScale-- }, enabled = displayScale > 0) { Text("−") }
                    Text(displayScale.toString())
                    TextButton(
                        onClick = { displayScale++ },
                        enabled = displayScale < currency.minorUnits,
                    ) { Text("+") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(symbol.trim().ifEmpty { currency.code.code }, displayScale) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
