package com.corriente.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Цвета дохода/расхода текущей темы (UI-1.1). Вне [CorrienteTheme] — светлая пара. */
val LocalCorrienteColors = staticCompositionLocalOf { LightMoneyColors }

/**
 * T5.5 / UI-1.1: Material You (динамическая палитра из обоев, Android 12+) либо собственная палитра
 * Corriente; тёмная тема, типографика и формы — свои.
 */
@Composable
fun CorrienteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> CorrienteDarkColors
        else -> CorrienteLightColors
    }
    CompositionLocalProvider(LocalCorrienteColors provides if (darkTheme) DarkMoneyColors else LightMoneyColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CorrienteTypography,
            shapes = CorrienteShapes,
            content = content,
        )
    }
}
