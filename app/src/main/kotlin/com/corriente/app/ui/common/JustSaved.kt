package com.corriente.app.ui.common

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Только что записанная форма-вводом операция: экран «Операции» показывает снекбар «Записано — Отменить».
 * Экран ввода закрывается сразу после записи, поэтому весточка живёт отдельно от обоих экранов; тот, кто её
 * показал, обнуляет [pending].
 */
object JustSaved {
    data class Entry(val txnId: String, val text: String)

    val pending = MutableStateFlow<Entry?>(null)
}
