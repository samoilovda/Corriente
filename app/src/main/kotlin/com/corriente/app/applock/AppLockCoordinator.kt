package com.corriente.app.applock

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.corriente.app.AppContainer
import com.corriente.data.applock.shouldPromptForUnlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * R5.2: отслеживает переход ВСЕГО приложения (не одной Activity) из фона на передний план —
 * виджет открывает [com.corriente.app.quick.QuickExpenseActivity] отдельной Activity поверх
 * убитого процесса, и её тоже нужно запереть (критерий приёмки: «быстрый ввод — тоже под
 * замком»); переключение между СВОИМИ же Activity (например, «Настройки» → быстрый ввод, если
 * когда-нибудь появится такой переход) не должно спрашивать разблокировку повторно.
 *
 * Используется [Application.registerActivityLifecycleCallbacks], а не
 * `androidx.lifecycle:lifecycle-process` (`ProcessLifecycleOwner`) — тот не входит в список
 * разрешённых зависимостей (BUILD_PLAN.md §1.3), а нужный здесь примитив, «число запущенных
 * Activity процесса», даёт сам Android SDK без единой лишней зависимости.
 */
class AppLockCoordinator(
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) : Application.ActivityLifecycleCallbacks {

    private var startedCount = 0

    /**
     * `null` — решение ещё не готово (настройки ещё читаются); экран-гейт в этот момент не
     * показывает НИЧЕГО — ни контент, ни явный замок — чтобы не мигнуть настоящими данными,
     * пока асинхронная проверка не завершилась. `true` — нужен экран блокировки.
     */
    private val _locked = MutableStateFlow<Boolean?>(null)
    val locked: StateFlow<Boolean?> = _locked

    override fun onActivityStarted(activity: Activity) {
        if (startedCount == 0) {
            _locked.value = null
            scope.launch {
                val config = container.appLockSettings.current()
                _locked.value = shouldPromptForUnlock(config.mode, config.lastUnlockAtMs, now())
            }
        }
        startedCount++
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount = (startedCount - 1).coerceAtLeast(0)
    }

    /** Зовётся после успешного прохождения биометрии/PIN — открывает контент и запоминает время. */
    fun onUnlocked() {
        _locked.value = false
        scope.launch { container.appLockSettings.recordUnlock(now()) }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
