package com.corriente.app.ui.autobackup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.backup.AutoBackupWorker
import com.corriente.data.backup.BACKUP_RETENTION_CHOICES
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** T5.1 / F1.3: экран «Автобэкап» — папка, включатель, статус последнего запуска, ротация. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoBackupScreen(
    onBack: () -> Unit,
    viewModel: AutoBackupViewModel = viewModel(factory = AutoBackupViewModel.factory()),
) {
    val state by viewModel.uiState.collectAsState()

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) viewModel.setFolder(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.autobackup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.autobackup_folder)) },
                supportingContent = {
                    Text(state.folderLabel ?: stringResource(R.string.autobackup_folder_none))
                },
                modifier = Modifier.clickable { folderPicker.launch(null) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.autobackup_enabled)) },
                supportingContent = { Text(stringResource(R.string.autobackup_enabled_hint, state.retention)) },
                trailingContent = {
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = viewModel::setEnabled,
                        enabled = state.folderLabel != null,
                    )
                },
            )

            HorizontalDivider()

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.autobackup_retention), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BACKUP_RETENTION_CHOICES.forEach { n ->
                        FilterChip(
                            selected = state.retention == n,
                            onClick = { viewModel.setRetention(n) },
                            label = { Text(n.toString()) },
                        )
                    }
                }
            }

            HorizontalDivider()

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(lastRunText(state))
                if (state.lastResult != null && state.lastResult != AutoBackupWorker.RESULT_OK) {
                    Text(
                        stringResource(R.string.autobackup_last_error, state.lastResult!!),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(
                onClick = viewModel::backupNow,
                enabled = state.folderLabel != null,
                modifier = Modifier.padding(16.dp),
            ) { Text(stringResource(R.string.autobackup_now)) }
        }
    }
}

@Composable
private fun lastRunText(state: AutoBackupUiState): String {
    val at = state.lastRunAt ?: return stringResource(R.string.autobackup_last_run_never)
    val stamp = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
    val outcome = if (state.lastResult == AutoBackupWorker.RESULT_OK) {
        " · " + stringResource(R.string.autobackup_last_ok)
    } else {
        ""
    }
    return stringResource(R.string.autobackup_last_run, stamp) + outcome
}
