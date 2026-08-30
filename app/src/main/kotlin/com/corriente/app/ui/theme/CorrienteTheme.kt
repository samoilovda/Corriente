package com.corriente.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Пока — стандартная Material3-палитра без кастомизации: полировка темы вынесена в этап 5
 * (BUILD_PLAN.md §7, T5.5). Дальше сюда добавятся Material You (dynamicColorScheme) и
 * цвета для категорий/типов операций.
 */
private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun CorrienteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
