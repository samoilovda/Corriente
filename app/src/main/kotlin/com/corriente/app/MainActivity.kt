package com.corriente.app

import android.content.Intent
import android.os.Bundle
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

    /**
     * R4.4: intent ярлыка приложения, ждущий обработки [CorrienteNavHost]'ом — с холодного
     * старта берётся из [getIntent], при уже запущенной Activity обновляется в [onNewIntent]
     * (Activity не `singleTask`, но лаунчер может переиспользовать существующий таск).
     */
    private var pendingDeepLinkIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge() // F2.4: Android 15+ включает edge-to-edge принудительно — берём под контроль
        super.onCreate(savedInstanceState)
        pendingDeepLinkIntent = intent
        setContent {
            CorrienteTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // R5.2: быстрый ввод из виджета — тоже под замком, но списки операций/отчёты
                    // за ним тем более.
                    AppLockGate {
                        CorrienteNavHost(
                            deepLinkIntent = pendingDeepLinkIntent,
                            onDeepLinkConsumed = { pendingDeepLinkIntent = null },
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
