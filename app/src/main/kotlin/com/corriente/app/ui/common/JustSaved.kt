package com.corriente.app.ui.common

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Только что записанная форма-вводом операция: экран «Операции» показывает снекбар «Записано — Отменить».
 * Экран ввода закрывается сразу после записи, поэтому весточка живёт отдельно от обоих экранов; тот, кто её
 * показал, обнуляет [pending].
 *
 * W8: весточка помнит время записи. Если «Операции» не показались сразу (ввод с виджета закрывает приложение), её
 * нельзя показать позже — «Отменить» удалило бы давно записанную операцию.
 */
object JustSaved {
    data class Entry(val txnId: String, val text: String, val savedAtMs: Long = System.currentTimeMillis()) {
        /** Сколько ещё можно предлагать «Отменить» в момент [nowMs]; 0 — уже поздно, весточку не показывать. */
        fun remainingMs(nowMs: Long, windowMs: Long = WINDOW_MS): Long =
            (savedAtMs + windowMs - nowMs).coerceIn(0, windowMs)
    }

    /** Окно «Отменить» после записи (как у удаления, R5.3). */
    const val WINDOW_MS = 5_000L

    val pending = MutableStateFlow<Entry?>(null)
}
