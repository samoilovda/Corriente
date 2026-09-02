package com.corriente.app.ui.autobackup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import com.corriente.app.backup.AutoBackupWorker
import com.corriente.app.backup.SafBackupFile
import com.corriente.app.corrienteContainer
import com.corriente.app.ui.settings.BackupResultDialog
import com.corriente.app.ui.settings.BackupViewModel
import com.corriente.data.backup.BACKUP_RETENTION_CHOICES
import com.corriente.data.backup.BackupSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * T5.1 / F1.3: экран «Автобэкап» — папка, включатель, статус последнего запуска, ротация.
 * R1.3: список файлов в папке с восстановлением из любого без файлового менеджера.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoBackupScreen(
    onBack: () -> Unit,
    viewModel: AutoBackupViewModel = viewModel(factory = AutoBackupViewModel.factory()),
    backupViewModel: BackupViewModel = viewModel(
        factory = BackupViewModel.factory(
            corrienteContainer().backupRepository,
            snapshotBeforeRestore = corrienteContainer()::snapshotDatabaseBeforeRestore,
        ),
    ),
) {
    val state by viewModel.uiState.collectAsState()
    val files by viewModel.backupFiles.collectAsState()
    val busy by backupViewModel.busy.collectAsState()
    val result by backupViewModel.result.collectAsState()
    val verifying by viewModel.verifying.collectAsState()
    val verifyResult by viewModel.verifyResult.collectAsState()
    var pendingRestore by remember { mutableStateOf<SafBackupFile?>(null) }

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
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.autobackup_folder)) },
                supportingContent = {
                    Text(state.folderLabel ?: stringResource(R.string.autobackup_folder_none))
                },
                modifier = Modifier.clickable { folderPicker.launch(null) },
            )
            // R1.1: SAF-дерево Google Диска исторически недоступно как источник для автобэкапа —
            // явное предупреждение, чтобы не выбирали его "на глаз" и не удивлялись, что запись не идёт.
            Text(
                stringResource(R.string.autobackup_drive_note),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            if (state.folderLabel != null) {
                HorizontalDivider()
                Text(
                    stringResource(R.string.autobackup_files_title),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(16.dp),
                )
                if (files.isEmpty()) {
                    Text(
                        stringResource(R.string.autobackup_files_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                } else {
                    files.forEach { file ->
                        BackupFileRow(
                            file = file,
                            busy = busy,
                            verifying = verifying,
                            onRestore = { pendingRestore = file },
                            onVerify = { viewModel.verify(file) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    pendingRestore?.let { file ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    backupViewModel.restore(viewModel.openBackupFile(file))
                }) { Text(stringResource(R.string.backup_import_confirm_button)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    result?.let { current -> BackupResultDialog(current, onDismiss = backupViewModel::consumeResult) }

    verifyResult?.let { current -> VerifyResultDialog(current, onDismiss = viewModel::consumeVerifyResult) }
}

@Composable
private fun BackupFileRow(
    file: SafBackupFile,
    busy: Boolean,
    verifying: Boolean,
    onRestore: () -> Unit,
    onVerify: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(file.name) },
        supportingContent = { Text("${formatFileDate(file.lastModified)} · ${formatFileSize(file.size)}") },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onVerify, enabled = !verifying) {
                    Text(stringResource(R.string.backup_verify_action))
                }
                TextButton(onClick = onRestore, enabled = !busy) {
                    Text(stringResource(R.string.backup_restore_action))
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** R1.4: сводка проверки файла — совпадающие/расходящиеся числа рядом, без риска для реальных данных. */
@Composable
private fun VerifyResultDialog(result: VerifyResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_verify_title)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        text = {
            when (result) {
                is VerifyResult.Success -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryText(R.string.backup_verify_file, result.fileSummary)
                    SummaryText(R.string.backup_verify_current, result.currentSummary)
                }
                is VerifyResult.Invalid -> {
                    val shown = result.problems.take(3).joinToString("\n") { "• $it" }
                    val rest = result.problems.size - 3
                    Text(
                        stringResource(R.string.backup_verify_invalid) + "\n" + shown +
                            if (rest > 0) "\n" + stringResource(R.string.backup_result_invalid_more, rest) else "",
                    )
                }
                VerifyResult.Failed -> Text(stringResource(R.string.backup_verify_failed))
            }
        },
    )
}

@Composable
private fun SummaryText(labelRes: Int, summary: BackupSummary) {
    Column {
        Text(stringResource(labelRes, summary.accounts, summary.categories, summary.transactions))
        if (summary.sumsByCurrency.isNotEmpty()) {
            val sums = summary.sumsByCurrency.entries.sortedBy { it.key }.joinToString(", ") { "${it.key} ${it.value}" }
            Text(
                "${stringResource(R.string.backup_verify_sums)}: $sums",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun formatFileDate(epochMs: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

@Composable
private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> stringResource(R.string.autobackup_size_mb, bytes / 1_000_000.0)
    bytes >= 1_000 -> stringResource(R.string.autobackup_size_kb, bytes / 1_000.0)
    else -> stringResource(R.string.autobackup_size_bytes, bytes)
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
