package com.corriente.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.backup.ShareBackupCache
import com.corriente.app.corrienteContainer
import java.io.File

/** Настройки (T1.2 — валюты; T1.4 — категории; T1.9 — экспорт/восстановление бэкапа). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenCurrencies: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenImportHistory: () -> Unit,
    onOpenWidgetSettings: () -> Unit,
    onOpenAutoBackup: () -> Unit,
    backupViewModel: BackupViewModel = viewModel(
        factory = BackupViewModel.factory(
            corrienteContainer().backupRepository,
            snapshotBeforeRestore = corrienteContainer()::snapshotDatabaseBeforeRestore,
        ),
    ),
) {
    val context = LocalContext.current
    val result by backupViewModel.result.collectAsState()
    val busy by backupViewModel.busy.collectAsState()
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.let(backupViewModel::export)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImportUri = uri }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.currencies_title)) },
                modifier = Modifier.clickable(onClick = onOpenCurrencies),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.categories_title)) },
                modifier = Modifier.clickable(onClick = onOpenCategories),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.widget_settings)) },
                supportingContent = { Text(stringResource(R.string.widget_settings_hint)) },
                modifier = Modifier.clickable(onClick = onOpenWidgetSettings),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.import_monefy)) },
                supportingContent = { Text(stringResource(R.string.import_monefy_hint)) },
                modifier = Modifier.clickable(onClick = onOpenImport),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.import_history_title)) },
                supportingContent = { Text(stringResource(R.string.import_history_hint)) },
                modifier = Modifier.clickable(onClick = onOpenImportHistory),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_export)) },
                supportingContent = { Text(stringResource(R.string.backup_export_hint)) },
                modifier = Modifier.clickable(enabled = !busy) { exportLauncher.launch("corriente-backup.json") },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_import)) },
                supportingContent = { Text(stringResource(R.string.backup_import_hint)) },
                modifier = Modifier.clickable(enabled = !busy) { importLauncher.launch(arrayOf("application/json")) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_share)) },
                supportingContent = { Text(stringResource(R.string.backup_share_hint)) },
                modifier = Modifier.clickable(enabled = !busy) {
                    // R1.2: временный файл в cache/share, отдаётся системному шерингу через
                    // FileProvider — своей сети приложение не заводит (I-24), сеть трогает
                    // то приложение, которое пользователь выберет в системном листе.
                    val dir = ShareBackupCache.dir(context).apply { mkdirs() }
                    val file = File(dir, "corriente-backup-${System.currentTimeMillis()}.json")
                    backupViewModel.exportForShare(file) { shared ->
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shared)
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.backup_share)))
                    }
                },
            )
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.autobackup_title)) },
                supportingContent = { Text(stringResource(R.string.autobackup_hint)) },
                modifier = Modifier.clickable(onClick = onOpenAutoBackup),
            )
        }
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    context.contentResolver.openInputStream(uri)?.let(backupViewModel::restore)
                }) { Text(stringResource(R.string.backup_import_confirm_button)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    result?.let { current ->
        AlertDialog(
            onDismissRequest = backupViewModel::consumeResult,
            confirmButton = {
                TextButton(onClick = backupViewModel::consumeResult) { Text(stringResource(R.string.ok)) }
            },
            text = {
                Text(
                    when (current) {
                        BackupResult.Exported -> stringResource(R.string.backup_result_exported)
                        BackupResult.Imported -> stringResource(R.string.backup_result_imported)
                        is BackupResult.VersionMismatch ->
                            stringResource(R.string.backup_result_version, current.fileVersion, current.appVersion)
                        is BackupResult.Invalid -> {
                            val shown = current.problems.take(3).joinToString("\n") { "• $it" }
                            val rest = current.problems.size - 3
                            stringResource(R.string.backup_result_invalid) + "\n" + shown +
                                if (rest > 0) "\n" + stringResource(R.string.backup_result_invalid_more, rest) else ""
                        }
                        BackupResult.Failed -> stringResource(R.string.backup_result_failed)
                    },
                )
            },
        )
    }
}
