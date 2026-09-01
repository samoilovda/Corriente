package com.corriente.app.ui.widgetsettings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.corrienteContainer

/** T4.4: закрепление 1–3 валют и выбор активного счёта для быстрого ввода из виджета. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSettingsScreen(
    onBack: () -> Unit,
    viewModel: WidgetSettingsViewModel = viewModel(
        factory = WidgetSettingsViewModel.factory(
            corrienteContainer().accountRepository,
            corrienteContainer().currencyRepository,
            corrienteContainer().txnRepository,
            corrienteContainer().widgetConfigStore,
        ),
    ),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.widget_pinned_currencies),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp),
            )
            state.currencies.forEach { row ->
                ListItem(
                    headlineContent = { Text("${row.code}  ${row.symbol}") },
                    trailingContent = {
                        Checkbox(
                            checked = row.pinned,
                            onCheckedChange = null,
                            enabled = row.pinned || state.canPinMore,
                        )
                    },
                    modifier = Modifier.clickable(enabled = row.pinned || state.canPinMore) {
                        viewModel.toggleCurrency(row.code)
                    },
                )
            }

            HorizontalDivider()
            Text(
                stringResource(R.string.widget_active_account),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(16.dp),
            )
            state.accounts.forEach { row ->
                ListItem(
                    headlineContent = { Text(row.name) },
                    supportingContent = { Text(row.currency) },
                    trailingContent = {
                        RadioButton(selected = row.active, onClick = { viewModel.setActiveAccount(row.id) })
                    },
                    modifier = Modifier.clickable { viewModel.setActiveAccount(row.id) },
                )
            }
        }
    }
}
