package com.corriente.app

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.corriente.app.applock.AppLockGate
import com.corriente.app.navigation.CorrienteNavHost
import com.corriente.app.ui.theme.CorrienteTheme

/**
 * R5.2: наследуется от [FragmentActivity], а не `ComponentActivity` — этого требует
 * `androidx.biometric.BiometricPrompt` (см. [AppLockGate]). `FragmentActivity` расширяет
 * `ComponentActivity`, так что `setContent`/edge-to-edge не меняются.
 */
class MainActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.corriente.app.AppLocale.wrap(newBase))
    }

    /**
     * R4.4: intent ярлыка приложения, ждущий обработки [CorrienteNavHost]'ом — с холодного
     * старта берётся из [getIntent], при уже запущенной Activity обновляется в [onNewIntent]
     * (Activity не `singleTask`, но лаунчер может переиспользовать существующий таск).
     */
    private var pendingDeepLinkIntent by mutableStateOf<Intent?>(null)

    /** Синхронизация: при каждом возвращении в приложение — обмен сразу, чтобы увидеть внесённое на других устройствах. */
    override fun onStart() {
        super.onStart()
        val app = application as CorrienteApplication
        lifecycleScope.launch {
            if (app.container.syncController.enabled.first()) com.corriente.app.sync.SyncScheduler.requestSoon(app, delaySeconds = 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge() // F2.4: Android 15+ включает edge-to-edge принудительно — берём под контроль
        super.onCreate(savedInstanceState)
        // A2.8: при пересоздании (поворот, смерть процесса) intent ярлыка всё ещё лежит в getIntent() —
        // повторно его обрабатывать нельзя: стек навигации сбросился бы на экран ярлыка.
        if (savedInstanceState == null) pendingDeepLinkIntent = intent
        setContent {
            CorrienteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // R5.2: быстрый ввод из виджета — тоже под замком, но списки операций/отчёты
                    // за ним тем более.
                    AppLockGate {
                        CorrienteNavHost(
                            deepLinkIntent = pendingDeepLinkIntent,
                            onDeepLinkConsumed = {
                                pendingDeepLinkIntent = null
                                // обработанный ярлык больше не должен читаться из getIntent()
                                setIntent(Intent(intent).apply { data = null })
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingDeepLinkIntent = intent
    }
}
