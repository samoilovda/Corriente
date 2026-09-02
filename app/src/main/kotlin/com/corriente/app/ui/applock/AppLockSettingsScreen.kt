package com.corriente.app.ui.applock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.corriente.app.R
import com.corriente.app.applock.canUseAppLock
import com.corriente.app.corrienteContainer
import com.corriente.data.applock.AppLockMode

/**
 * R5.2: три режима, ничего сверх — вкл./выкл. режимов "каждое открытие" и "после 5 минут",
 * плюс честное предупреждение о том, что это не шифрование (`app_lock_disclaimer`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSettingsScreen(
    onBack: () -> Unit,
    viewModel: AppLockSettingsViewModel = viewModel(
        factory = AppLockSettingsViewModel.factory(corrienteContainer().appLockSettings),
    ),
) {
    val mode by viewModel.mode.collectAsState()
    val context = LocalContext.current
    var showNoAuthenticatorWarning by remember { mutableStateOf(false) }

    fun selectMode(target: AppLockMode) {
        if (target == AppLockMode.OFF || canUseAppLock(context)) {
            showNoAuthenticatorWarning = false
            viewModel.setMode(target)
        } else {
            // Включать режим, который заведомо нечем пройти (нет ни биометрии, ни PIN/графического
            // ключа устройства), означало бы запереть пользователя от собственных данных.
            showNoAuthenticatorWarning = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_lock_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                stringResource(R.string.app_lock_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            HorizontalDivider()
            listOf(
                AppLockMode.OFF to R.string.app_lock_mode_off,
                AppLockMode.EVERY_OPEN to R.string.app_lock_mode_every_open,
                AppLockMode.AFTER_5_MINUTES to R.string.app_lock_mode_after_5_minutes,
            ).forEach { (candidate, labelRes) ->
                ListItem(
                    headlineContent = { Text(stringResource(labelRes)) },
                    leadingContent = { RadioButton(selected = mode == candidate, onClick = { selectMode(candidate) }) },
                    modifier = Modifier.clickable { selectMode(candidate) },
                )
            }
            if (showNoAuthenticatorWarning) {
                Text(
                    stringResource(R.string.app_lock_no_authenticator),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
