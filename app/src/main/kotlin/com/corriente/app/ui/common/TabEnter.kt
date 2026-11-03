package com.corriente.app.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Счётчик переходов между вкладками (разделами) приложения. Экраны разделов по нему отличают «пришли с другой
 * вкладки» от «вернулись из своей формы/вложенного экрана» — например, кольцо «Операций» вырастает заново только
 * в первом случае.
 */
object TabEnter {
    var version by mutableIntStateOf(0)
        private set

    fun onTabSwitched() {
        version++
    }
}
