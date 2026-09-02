package com.corriente.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.corriente.app.R
import com.corriente.app.corrienteContainer
import com.corriente.data.backup.shouldWarnAboutBackup
import kotlinx.coroutines.flow.combine

private data class ReminderInfo(val warn: Boolean, val daysAgo: Long?)

private val NEVER = ReminderInfo(warn = false, daysAgo = null)

/**
 * Плашка «Последний бэкап: N дней назад» (R1.5) — на «Настройках» и «Счетах». Пропадает сама,
 * как только `shouldWarnAboutBackup` перестаёт быть true (успешный бэкап обновляет
 * `auto_backup.last_run_at`, эта плашка подписана на тот же Flow, отдельного события не нужно).
 */
@Composable
fun BackupReminderBanner(onClick: () -> Unit) {
    val container = corrienteContainer()
    val info by produceState(initialValue = NEVER) {
        combine(container.autoBackupSettings.config, container.txnRepository.observeCount()) { config, count ->
            val now = System.currentTimeMillis()
            ReminderInfo(
                warn = shouldWarnAboutBackup(config.lastRunAt, config.enabled, count, now),
                daysAgo = config.lastRunAt?.let { (now - it) / (24 * 60 * 60 * 1000) },
            )
        }.collect { value = it }
    }

    if (!info.warn) return

    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = info.daysAgo?.let { stringResource(R.string.backup_reminder_days, it.toInt()) }
                ?: stringResource(R.string.backup_reminder_never),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}
