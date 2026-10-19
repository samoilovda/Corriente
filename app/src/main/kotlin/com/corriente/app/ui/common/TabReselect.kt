package com.corriente.app.ui.common

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Повторный тап по уже открытой вкладке нижней панели — экран прокручивается к началу (верхней панели с
 * действиями больше нет, они в начале списка). Ключ — маршрут вкладки.
 */
object TabReselect {
    val events = MutableSharedFlow<String>(extraBufferCapacity = 1)
}
