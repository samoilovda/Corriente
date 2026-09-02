package com.corriente.app.quick

import com.corriente.data.widget.WidgetConfigStore

/**
 * Запись из окна смены активного счёта виджета (F0.6). Вынесено из Activity, чтобы:
 *  - запускать в `lifecycleScope`, а не в скоупе композиции (переживает пересоздание Activity);
 *  - ошибку репозитория возвращать как [Result], а не ронять процесс.
 *
 * R4.2: аналогичная запись расхода/дохода теперь идёт через [QuickEntryViewModel]
 * (`WritingViewModel.launchWrite` даёт то же самое — сообщение вместо падения процесса —
 * плюс переживает пересоздание Activity через `viewModelScope`).
 */
suspend fun changeActiveAccount(configStore: WidgetConfigStore, accountId: String): Result<Unit> = runCatching {
    configStore.setActiveAccount(accountId)
}
