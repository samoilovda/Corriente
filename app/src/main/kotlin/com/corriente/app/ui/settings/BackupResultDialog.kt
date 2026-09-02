package com.corriente.app.ui.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.corriente.app.R

/**
 * Итог экспорта/восстановления (T1.9/F1.4) — общий для «Настроек» и «Автобэкапа» (R1.3),
 * которые оба зовут [com.corriente.app.ui.settings.BackupViewModel.restore].
 */
@Composable
fun BackupResultDialog(result: BackupResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
        text = {
            Text(
                when (result) {
                    BackupResult.Exported -> stringResource(R.string.backup_result_exported)
                    BackupResult.Imported -> stringResource(R.string.backup_result_imported)
                    is BackupResult.VersionMismatch ->
                        stringResource(R.string.backup_result_version, result.fileVersion, result.appVersion)
                    is BackupResult.Invalid -> {
                        val shown = result.problems.take(3).joinToString("\n") { "• $it" }
                        val rest = result.problems.size - 3
                        stringResource(R.string.backup_result_invalid) + "\n" + shown +
                            if (rest > 0) "\n" + stringResource(R.string.backup_result_invalid_more, rest) else ""
                    }
                    BackupResult.Failed -> stringResource(R.string.backup_result_failed)
                },
            )
        },
    )
}
