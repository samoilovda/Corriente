package com.corriente.app.ui.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.sync.JoinMode
import com.corriente.app.sync.SyncError
import com.corriente.app.sync.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «Настройки → Синхронизация» (docs/SYNC_DESIGN.md §1, §6): папка Syncthing, имя устройства, начало/подключение, статус,
 * список устройств. Данные переносит Syncthing; приложение только читает и пишет файлы в папке (I-24).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(onBack: () -> Unit, viewModel: SyncViewModel = viewModel(factory = SyncViewModel.factory())) {
    val status by viewModel.status.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val ownId by viewModel.ownId.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val question by viewModel.question.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var folderError by rememberSaveable { mutableStateOf(false) }
    var confirmDisable by rememberSaveable { mutableStateOf(false) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) folderError = !viewModel.setFolder(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            ListItem(
                headlineContent = { Text(stringResource(R.string.sync_folder)) },
                supportingContent = {
                    Text(status.folderUri?.toUri()?.lastPathSegment?.substringAfterLast(':') ?: stringResource(R.string.autobackup_folder_none))
                },
                modifier = Modifier.clickable(enabled = !status.enabled && !busy) { folderPicker.launch(null) },
            )
            if (folderError) {
                Text(
                    stringResource(R.string.autobackup_folder_unavailable),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (!status.enabled) {
                NotEnabledBody(
                    name = name,
                    defaultName = viewModel.defaultDeviceName,
                    onName = { name = it },
                    folderChosen = status.folderUri != null,
                    busy = busy,
                    onStartHere = { viewModel.startHere(name) },
                    onJoin = { viewModel.requestJoin(name) },
                )
            } else {
                EnabledBody(
                    status = status,
                    devices = devices.map { DeviceRow(it.name.ifBlank { it.deviceId.take(8) }, it.deviceId == ownId, it.primary) },
                    busy = busy,
                    onSyncNow = viewModel::syncNow,
                    onCleanup = viewModel::cleanup,
                    onDisable = { confirmDisable = true },
                )
            }
        }
    }

    when (question) {
        JoinQuestion.EmptyFolder -> AlertDialog(
            onDismissRequest = viewModel::dismissQuestion,
            text = { Text(stringResource(R.string.sync_join_empty_folder)) },
            confirmButton = { TextButton(onClick = viewModel::dismissQuestion) { Text(stringResource(R.string.ok)) } },
        )
        JoinQuestion.HasLocalData -> AlertDialog(
            onDismissRequest = viewModel::dismissQuestion,
            title = { Text(stringResource(R.string.sync_join_has_data_title)) },
            text = { Text(stringResource(R.string.sync_join_has_data_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.join(name, JoinMode.REPLACE) }) { Text(stringResource(R.string.sync_join_replace)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.join(name, JoinMode.MERGE) }) { Text(stringResource(R.string.sync_join_merge)) }
            },
        )
        null -> Unit
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            text = { Text(stringResource(R.string.sync_disable_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmDisable = false; viewModel.disable() }) { Text(stringResource(R.string.sync_disable)) }
            },
            dismissButton = { TextButton(onClick = { confirmDisable = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private data class DeviceRow(val name: String, val isThis: Boolean, val primary: Boolean)

@Composable
private fun NotEnabledBody(
    name: String,
    defaultName: String,
    onName: (String) -> Unit,
    folderChosen: Boolean,
    busy: Boolean,
    onStartHere: () -> Unit,
    onJoin: () -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.sync_intro), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            singleLine = true,
            label = { Text(stringResource(R.string.sync_device_name)) },
            placeholder = { Text(defaultName) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onStartHere, enabled = folderChosen && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sync_start_here))
        }
        Text(stringResource(R.string.sync_start_here_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onJoin, enabled = folderChosen && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sync_join))
        }
        Text(stringResource(R.string.sync_join_hint), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EnabledBody(
    status: SyncStatus,
    devices: List<DeviceRow>,
    busy: Boolean,
    onSyncNow: () -> Unit,
    onCleanup: () -> Unit,
    onDisable: () -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(
                if (status.primary) R.string.sync_state_primary else R.string.sync_state_joined,
                status.deviceName.orEmpty(),
            ),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(syncStatusLine(status), style = MaterialTheme.typography.bodyMedium)
        status.lastError?.let { error ->
            Text(syncErrorText(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (status.newerSchemaDevices.isNotEmpty()) {
            Text(
                stringResource(R.string.sync_newer_schema, status.newerSchemaDevices.joinToString(", ")),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (status.deferred > 0) {
            Text(stringResource(R.string.sync_deferred, status.deferred), style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onSyncNow, enabled = !busy) { Text(stringResource(R.string.sync_now)) }
        // S5: очистка журнала в общей папке — данные не удаляются.
        OutlinedButton(onClick = onCleanup, enabled = !busy) { Text(stringResource(R.string.sync_cleanup)) }
        Text(stringResource(R.string.sync_cleanup_hint), style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
    Text(stringResource(R.string.sync_devices), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(16.dp))
    devices.forEach { d ->
        ListItem(
            headlineContent = { Text(d.name) },
            supportingContent = {
                val parts = listOfNotNull(
                    stringResource(R.string.sync_device_this).takeIf { d.isThis },
                    stringResource(R.string.sync_device_primary).takeIf { d.primary },
                )
                if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
            },
        )
    }
    HorizontalDivider()
    TextButton(onClick = onDisable, enabled = !busy, modifier = Modifier.padding(8.dp)) {
        Text(stringResource(R.string.sync_disable))
    }
}

/** «Последний обмен: 10.10 21:04» / «Обмена ещё не было». Используется и в «Настройках». */
@Composable
fun syncStatusLine(status: SyncStatus): String = when {
    !status.enabled -> stringResource(R.string.sync_off)
    status.lastRunAt == null -> stringResource(R.string.sync_never)
    else -> stringResource(R.string.sync_last_run, SimpleDateFormat("dd.MM HH:mm", Locale.ROOT).format(Date(status.lastRunAt)))
}

@Composable
fun syncErrorText(error: SyncError): String = when (error) {
    SyncError.FolderUnavailable -> stringResource(R.string.sync_error_folder)
    SyncError.Integrity -> stringResource(R.string.sync_error_integrity)
    is SyncError.Other -> stringResource(R.string.sync_error_other, error.message)
}
