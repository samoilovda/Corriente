package com.corriente.app.ui.imports

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.corrienteContainer

/**
 * T3.3: обязательный dry-run импорта Monefy. Показывает, что будет создано, что требует
 * проверки (NEEDS_REVIEW) и какие строки не разобрались. Запись — только по кнопке
 * «Импортировать» (I-19: повторный импорт того же файла идемпотентен).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: ImportViewModel = viewModel(
        factory = ImportViewModel.factory(corrienteContainer().monefyImportRepository),
    ),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "monefy.csv"
            context.contentResolver.openInputStream(uri)?.let { viewModel.preview(it, name) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                ImportUiState.Idle -> {
                    Button(onClick = {
                        picker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
                    }) { Text(stringResource(R.string.import_pick_file)) }
                }

                ImportUiState.Working -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.import_working))
                }

                is ImportUiState.Ready -> ReadyBody(s.summary, onConfirm = viewModel::confirm)

                is ImportUiState.Done -> {
                    Text(
                        stringResource(R.string.import_done, s.inserted, s.skipped),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedButton(onClick = viewModel::reset) { Text(stringResource(R.string.import_restart)) }
                }

                is ImportUiState.Failed -> {
                    Text(
                        stringResource(R.string.import_failed, s.message),
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(onClick = viewModel::reset) { Text(stringResource(R.string.import_restart)) }
                }
            }
        }
    }
}

@Composable
private fun ReadyBody(summary: ImportSummary, onConfirm: () -> Unit) {
    Text(stringResource(R.string.import_preview_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.import_summary_accounts, summary.accounts, summary.openingBalances))
    Text(stringResource(R.string.import_summary_categories, summary.categories))
    Text(stringResource(R.string.import_summary_operations, summary.operations))
    Text(stringResource(R.string.import_summary_transfers, summary.transfers))
    if (summary.unpairedHalves > 0) {
        Text(stringResource(R.string.import_summary_unpaired, summary.unpairedHalves))
    }

    if (summary.reviews.isNotEmpty()) {
        HorizontalDivider()
        Text(
            stringResource(R.string.import_reviews_title, summary.reviews.size),
            style = MaterialTheme.typography.titleSmall,
        )
        summary.reviews.forEach { Text("• ${it.message}") }
    }

    if (summary.errors.isNotEmpty()) {
        HorizontalDivider()
        Text(
            stringResource(R.string.import_errors_title, summary.errors.size),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error,
        )
        summary.errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
    }

    HorizontalDivider()
    Text(stringResource(R.string.import_confirm_hint), style = MaterialTheme.typography.bodySmall)
    Button(onClick = onConfirm) { Text(stringResource(R.string.import_confirm)) }
}
