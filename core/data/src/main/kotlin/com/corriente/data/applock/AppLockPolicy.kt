package com.corriente.data.applock

/**
 * Режим блокировки приложения (R5.2). Данные при этом НЕ шифруются — это защита от случайного
 * просмотра телефона в чужих руках, а не от изъятия устройства (см. текст настройки в :app).
 */
enum class AppLockMode {
    /** Блокировки нет — поведение до R5.2. */
    OFF,

    /** Подтверждение при каждом возврате приложения на передний план. */
    EVERY_OPEN,

    /** Подтверждение только если с последней успешной разблокировки прошло 5+ минут. */
    AFTER_5_MINUTES,
}

/** Настройки блокировки + время последнего успешного прохождения (ключ для [shouldPromptForUnlock]). */
data class AppLockConfig(
    val mode: AppLockMode = AppLockMode.OFF,
    val lastUnlockAtMs: Long? = null,
)

/** Порог режима [AppLockMode.AFTER_5_MINUTES]. */
const val APP_LOCK_AFTER_MINUTES_THRESHOLD_MS: Long = 5 * 60_000L

/**
 * Чистая функция «нужно ли запрашивать разблокировку сейчас» (R5.2, критерий приёмки).
 * Вызывается ровно один раз за переход приложения из фона на передний план — поэтому
 * [AppLockMode.EVERY_OPEN] сводится к «всегда да»: пока приложение остаётся на переднем плане,
 * эта функция повторно не спрашивается.
 *
 * @param nowMs текущее время (внедряется, а не `System.currentTimeMillis()`, ради тестируемости).
 */
fun shouldPromptForUnlock(mode: AppLockMode, lastUnlockAtMs: Long?, nowMs: Long): Boolean = when (mode) {
    AppLockMode.OFF -> false
    AppLockMode.EVERY_OPEN -> true
    AppLockMode.AFTER_5_MINUTES ->
        lastUnlockAtMs == null || nowMs - lastUnlockAtMs >= APP_LOCK_AFTER_MINUTES_THRESHOLD_MS
}
