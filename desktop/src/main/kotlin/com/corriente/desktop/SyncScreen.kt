package com.corriente.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.corriente.data.sync.DeviceInfo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JFileChooser

/**
 * Синхронизация с телефоном через папку Syncthing (docs/SYNC_DESIGN.md). Компьютер обычно подключается к уже начатой
 * на телефоне синхронизации; импорт Monefy — только на телефоне.
 */
@Composable
fun SyncScreen(c: DesktopContainer) {
    val status by c.sync.status.collectAsState(DesktopSyncStatus())
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var devices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var ownId by remember { mutableStateOf<String?>(null) }
    var askJoin by remember { mutableStateOf(false) }
    var emptyFolder by remember { mutableStateOf(false) }

    LaunchedEffect(status.lastRunAt, status.folder) {
        devices = c.sync.devices().sortedBy { it.name.lowercase() }
        ownId = c.sync.ownDeviceId()
    }

    fun work(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { block() }
            devices = c.sync.devices().sortedBy { it.name.lowercase() }
            ownId = c.sync.ownDeviceId()
            busy = false
        }
    }

    val deviceName = name.trim().ifBlank { c.sync.defaultDeviceName }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).width(720.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Синхронизация с телефоном", style = MaterialTheme.typography.headlineSmall)
        if (busy) LinearProgressIndicator()
        Text(
            "Данные переносит Syncthing: поставьте его на компьютер и телефон и расшарьте одну папку. Здесь выберите эту папку " +
                "на компьютере. Corriente сам в интернет не ходит — только читает и пишет файлы в папке.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Папка: ${status.folder ?: "не выбрана"}", modifier = Modifier.weight(1f))
            OutlinedButton(enabled = !status.enabled && !busy, onClick = {
                val chooser = JFileChooser().apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    dialogTitle = "Папка синхронизации Corriente"
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    scope.launch { c.sync.setFolder(chooser.selectedFile.absolutePath) }
                }
            }) { Text("Выбрать папку") }
        }

        if (!status.enabled) {
            OutlinedTextField(name, { name = it }, label = { Text("Имя компьютера") }, placeholder = { Text(c.sync.defaultDeviceName) }, singleLine = true)
            Button(enabled = status.folder != null && !busy, onClick = {
                work {
                    when {
                        c.sync.devices().isEmpty() -> emptyFolder = true
                        c.sync.hasLocalData() -> askJoin = true
                        else -> c.sync.join(deviceName, replace = null)
                    }
                }
            }) { Text("Подключиться к синхронизации") }
            Text(
                "Обычный путь: синхронизацию начинают на телефоне («Настройки → Синхронизация → Начать с этого устройства»), " +
                    "а компьютер подключается — данные придут из папки, когда Syncthing её перенесёт.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(enabled = status.folder != null && !busy, onClick = { work { c.sync.startHere(deviceName) } }) {
                Text("Начать синхронизацию с этого компьютера (если телефона в ней не будет)")
            }
        } else {
            Text(
                (if (status.primary) "Основное устройство «%s»" else "Компьютер «%s» подключён").format(status.deviceName.orEmpty()),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                status.lastRunAt?.let { "Последний обмен: " + SimpleDateFormat("dd.MM HH:mm", Locale.ROOT).format(Date(it)) }
                    ?: "Обмена ещё не было",
            )
            status.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (status.newerSchema) {
                Text("На другом устройстве более новая версия Corriente — обновите программу здесь.", color = MaterialTheme.colorScheme.error)
            }
            if (status.deferred > 0) Text("Ждут недостающих данных с других устройств: ${status.deferred}")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !busy, onClick = { work { c.sync.runOnce() } }) { Text("Синхронизировать сейчас") }
                OutlinedButton(enabled = !busy, onClick = { work { c.sync.cleanup() } }) { Text("Очистить журнал синхронизации") }
                TextButton(enabled = !busy, onClick = { work { c.sync.disable() } }) { Text("Отключить") }
            }
            Text(
                "Очистка убирает из общей папки старые файлы, которые уже применили все устройства. Данные не удаляются.",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            Text("Устройства в папке", style = MaterialTheme.typography.titleSmall)
            devices.forEach { d ->
                val tags = listOfNotNull("это устройство".takeIf { d.deviceId == ownId }, "основное".takeIf { d.primary })
                Text("• ${d.name.ifBlank { d.deviceId.take(8) }}" + if (tags.isNotEmpty()) " — ${tags.joinToString(", ")}" else "")
            }
        }
    }

    if (emptyFolder) {
        AlertDialog(
            onDismissRequest = { emptyFolder = false },
            text = { Text("В папке пока нет данных синхронизации. Начните синхронизацию на телефоне и дождитесь, пока Syncthing перенесёт папку сюда.") },
            confirmButton = { TextButton(onClick = { emptyFolder = false }) { Text("Понятно") } },
        )
    }
    if (askJoin) {
        AlertDialog(
            onDismissRequest = { askJoin = false },
            title = { Text("На этом компьютере уже есть данные") },
            text = {
                Text(
                    "«Заменить» — удалить данные компьютера и взять данные из папки (перед этим сохранится полный бэкап в папку " +
                        "данных программы). «Объединить» — добавить данные компьютера к общим.",
                )
            },
            confirmButton = { TextButton(onClick = { askJoin = false; work { c.sync.join(deviceName, replace = true) } }) { Text("Заменить") } },
            dismissButton = { TextButton(onClick = { askJoin = false; work { c.sync.join(deviceName, replace = false) } }) { Text("Объединить") } },
        )
    }
}
