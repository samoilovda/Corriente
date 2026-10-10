package com.corriente.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.AppLocale
import com.corriente.app.R
import com.corriente.app.backup.ShareBackupCache
import com.corriente.app.corrienteContainer
import com.corriente.app.ui.common.BackupReminderBanner
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
    onOpenBudgets: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenAppLock: () -> Unit,
    onOpenSync: () -> Unit = {},
    backupViewModel: BackupViewModel = viewModel(
        factory = BackupViewModel.factory(
            corrienteContainer().backupRepository,
            snapshotBeforeRestore = corrienteContainer()::snapshotDatabaseBeforeRestore,
        ),
    ),
) {
    val context = LocalContext.current
    // Читаем ресурс в композиции (конфиг-осведомлённо), а не через context.getString в
    // обработчике клика — того требует lint-правило LocalContextGetResourceValueCall.
    val shareChooserTitle = stringResource(R.string.backup_share)
    val result by backupViewModel.result.collectAsState()
    val busy by backupViewModel.busy.collectAsState()
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var languageDialogOpen by remember { mutableStateOf(false) }
    val syncStatus by corrienteContainer().syncController.status.collectAsState(initial = com.corriente.app.sync.SyncStatus())

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            backupViewModel.exportTo { context.contentResolver.openOutputStream(uri) ?: throw java.io.FileNotFoundException(uri.toString()) }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImportUri = uri }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            // R1.5: плашка «Последний бэкап: N дней назад» — сама решает, показываться ли.
            BackupReminderBanner(onClick = onOpenAutoBackup)
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
            // Синхронизация: импорт и его откат — только на основном устройстве (docs/SYNC_DESIGN.md §10).
            val importAllowed = syncStatus.importAllowed
            ListItem(
                headlineContent = { Text(stringResource(R.string.import_monefy)) },
                supportingContent = {
                    Text(stringResource(if (importAllowed) R.string.import_monefy_hint else R.string.sync_import_primary_only))
                },
                modifier = Modifier.clickable(enabled = importAllowed, onClick = onOpenImport),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.import_history_title)) },
                supportingContent = {
                    Text(stringResource(if (importAllowed) R.string.import_history_hint else R.string.sync_import_primary_only))
                },
                modifier = Modifier.clickable(enabled = importAllowed, onClick = onOpenImportHistory),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.budgets_title)) },
                supportingContent = { Text(stringResource(R.string.budgets_hint)) },
                modifier = Modifier.clickable(onClick = onOpenBudgets),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.recurring_title)) },
                supportingContent = { Text(stringResource(R.string.recurring_hint)) },
                modifier = Modifier.clickable(onClick = onOpenRecurring),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.language_title)) },
                supportingContent = { Text(languageLabel(AppLocale.get(context))) },
                modifier = Modifier.clickable { languageDialogOpen = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_lock_title)) },
                supportingContent = { Text(stringResource(R.string.app_lock_hint)) },
                modifier = Modifier.clickable(onClick = onOpenAppLock),
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
                        context.startActivity(Intent.createChooser(sendIntent, shareChooserTitle))
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
            ListItem(
                headlineContent = { Text(stringResource(R.string.sync_title)) },
                supportingContent = {
                    val error = syncStatus.lastError
                    when {
                        !syncStatus.enabled -> Text(stringResource(R.string.sync_hint_off))
                        error != null -> Text(com.corriente.app.ui.sync.syncErrorText(error), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                        else -> Text(com.corriente.app.ui.sync.syncStatusLine(syncStatus))
                    }
                },
                modifier = Modifier.clickable(onClick = onOpenSync),
            )
        }
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = {
                val warning = if (syncStatus.enabled) "\n\n" + stringResource(R.string.sync_restore_warning) else ""
                Text(stringResource(R.string.backup_import_confirm_text) + warning)
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    backupViewModel.restoreFrom { context.contentResolver.openInputStream(uri) ?: throw java.io.FileNotFoundException(uri.toString()) }
                }) { Text(stringResource(R.string.backup_import_confirm_button)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    result?.let { current -> BackupResultDialog(current, onDismiss = backupViewModel::consumeResult) }

    if (languageDialogOpen) {
        val current = AppLocale.get(context)
        AlertDialog(
            onDismissRequest = { languageDialogOpen = false },
            title = { Text(stringResource(R.string.language_title)) },
            text = {
                Column {
                    AppLocale.supported.forEach { tag ->
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                languageDialogOpen = false
                                if (tag != current) {
                                    AppLocale.set(context, tag)
                                    (context as? android.app.Activity)?.recreate()
                                }
                            },
                        ) {
                            androidx.compose.material3.RadioButton(selected = tag == current, onClick = null)
                            Text(languageLabel(tag), Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { languageDialogOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Названия языков — на них самих (не переводятся), кроме «как в системе». */
@androidx.compose.runtime.Composable
private fun languageLabel(tag: String): String = when (tag) {
    "en" -> "English"
    "ru" -> "Русский"
    else -> stringResource(R.string.language_system)
}
