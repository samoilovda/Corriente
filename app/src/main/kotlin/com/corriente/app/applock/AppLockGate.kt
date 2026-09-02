package com.corriente.app.applock

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.corriente.app.CorrienteApplication
import com.corriente.app.R

/**
 * R5.2: оборачивает содержимое экрана — [com.corriente.app.MainActivity] и обе полупрозрачные
 * Activity быстрого ввода из виджета ([com.corriente.app.quick.QuickExpenseActivity],
 * [com.corriente.app.quick.ChangeActiveAccountActivity]) — так, чтобы каждая из них подчинялась
 * одному и тому же общему на процесс решению «заперто/нет» ([AppLockCoordinator]).
 *
 * Пока решение не готово ([AppLockCoordinator.locked] == null), не показывает ничего — ни
 * реальный контент, ни явный экран замка — чтобы данные не мелькнули на кадр раньше проверки.
 */
@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val app = LocalContext.current.applicationContext as CorrienteApplication
    val locked by app.appLockCoordinator.locked.collectAsState()

    when (locked) {
        null -> Box(Modifier.fillMaxSize())
        true -> AppLockScreen(onUnlocked = app.appLockCoordinator::onUnlocked)
        false -> content()
    }
}

@Composable
private fun AppLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    fun promptUnlock() {
        val fragmentActivity = activity ?: return
        if (!canUseAppLock(fragmentActivity)) {
            // На устройстве не осталось ни биометрии, ни PIN (сняли уже после включения замка
            // в настройках) — требовать разблокировку, которую нечем пройти, значит запереть
            // пользователя от его же данных навсегда. Замок в этом случае бессилен по своей
            // природе (это ограничение самого устройства, не приложения) — пропускаем без
            // подтверждения; предупреждение об отсутствии биометрии/PIN показывается заранее,
            // при включении режима в [com.corriente.app.ui.applock.AppLockSettingsScreen].
            onUnlocked()
            return
        }
        val executor = ContextCompat.getMainExecutor(fragmentActivity)
        val prompt = BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onUnlocked()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(fragmentActivity.getString(R.string.app_lock_prompt_title))
            .setSubtitle(fragmentActivity.getString(R.string.app_lock_prompt_subtitle))
            .setAllowedAuthenticators(APP_LOCK_ALLOWED_AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }

    // Предлагаем разблокировку сразу, не дожидаясь тапа — совпадает с ожиданием «спрашивает при
    // возврате из фона» из критерия приёмки, а не только по кнопке.
    LaunchedEffect(Unit) { promptUnlock() }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(96.dp))
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.app_lock_locked_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { promptUnlock() }) { Text(stringResource(R.string.app_lock_unlock_button)) }
        }
    }
}
