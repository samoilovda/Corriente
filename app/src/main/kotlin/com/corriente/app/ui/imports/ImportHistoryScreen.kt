package com.corriente.app.ui.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.corrienteContainer
import com.corriente.app.ui.common.rememberMessageSnackbarState
import com.corriente.data.imports.MonefyImportRepository.ImportBatchInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** F1.5: «История импортов» — прошлые импорты Monefy и откат любого из них. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportHistoryScreen(
    onBack: () -> Unit,
    viewModel: ImportHistoryViewModel = viewModel(
        factory = ImportHistoryViewModel.factory(corrienteContainer().monefyImportRepository),
    ),
) {
    val batches by viewModel.batches.collectAsState()
    val message by viewModel.messages.collectAsState()
    val snackbarState = rememberMessageSnackbarState(message, viewModel::consumeMessage)
    var pendingRollback by remember { mutableStateOf<ImportBatchInfo?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (batches.isEmpty()) {
            Text(
                stringResource(R.string.import_history_empty),
                Modifier.padding(padding).padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(batches, key = { it.id }) { batch ->
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(batch.fileName, style = MaterialTheme.typography.titleMedium)
                        Text(formatDate(batch.importedAt), style = MaterialTheme.typography.bodySmall)
                        Text(
                            stringResource(
                                R.string.import_history_summary,
                                batch.report.operations,
                                batch.report.transfers,
                                batch.report.categories,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { pendingRollback = batch }) {
                            Text(stringResource(R.string.import_history_rollback))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    pendingRollback?.let { batch ->
        AlertDialog(
            onDismissRequest = { pendingRollback = null },
            title = { Text(stringResource(R.string.import_history_rollback)) },
            text = { Text(stringResource(R.string.import_history_rollback_confirm, batch.fileName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rollback(batch.id)
                    pendingRollback = null
                }) { Text(stringResource(R.string.import_history_rollback)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRollback = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private fun formatDate(epochMs: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
