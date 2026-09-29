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

    /** Подтверждение только если приложение пробыло в фоне 5+ минут (A3.9). */
    AFTER_5_MINUTES,
}

/**
 * Настройки блокировки + метки времени для [shouldPromptForUnlock]: успешная разблокировка и (A3.9) момент ухода
 * приложения в фон.
 */
data class AppLockConfig(
    val mode: AppLockMode = AppLockMode.OFF,
    val lastUnlockAtMs: Long? = null,
    /** Когда всё приложение последний раз ушло в фон (не поворот экрана). */
    val lastBackgroundAtMs: Long? = null,
) {
    /**
     * A3.9: «последняя активность» — более поздняя из двух меток. Режим «через 5 минут» считает от неё: пять минут
     * в фоне, а не пять минут с момента разблокировки (иначе после десяти минут непрерывной работы любое
     * переключение на секунду снова спрашивало бы биометрию).
     */
    val lastActiveAtMs: Long? get() = listOfNotNull(lastUnlockAtMs, lastBackgroundAtMs).maxOrNull()
}

/** Порог режима [AppLockMode.AFTER_5_MINUTES] — время в фоне. */
const val APP_LOCK_AFTER_MINUTES_THRESHOLD_MS: Long = 5 * 60_000L

/**
 * Чистая функция «нужно ли запрашивать разблокировку сейчас» (R5.2, критерий приёмки).
 * Вызывается ровно один раз за переход приложения из фона на передний план — поэтому
 * [AppLockMode.EVERY_OPEN] сводится к «всегда да»: пока приложение остаётся на переднем плане,
 * эта функция повторно не спрашивается.
 *
 * @param lastUnlockAtMs метка «последней активности» ([AppLockConfig.lastActiveAtMs]): уход в фон или разблокировка.
 * @param nowMs текущее время (внедряется, а не `System.currentTimeMillis()`, ради тестируемости).
 */
fun shouldPromptForUnlock(mode: AppLockMode, lastUnlockAtMs: Long?, nowMs: Long): Boolean = when (mode) {
    AppLockMode.OFF -> false
    AppLockMode.EVERY_OPEN -> true
    AppLockMode.AFTER_5_MINUTES ->
        lastUnlockAtMs == null || nowMs - lastUnlockAtMs >= APP_LOCK_AFTER_MINUTES_THRESHOLD_MS
}
