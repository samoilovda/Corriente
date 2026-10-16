package com.corriente.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * UI-1.1: собственная палитра («течение» — бирюзово-синий seed #006780), тона сняты с M3-генератора.
 * Используется как фолбэк (Android < 12) и когда «Цвета из обоев» выключены (UI-1.5).
 */
internal val CorrienteLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF006780),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBCE9FF),
    onPrimaryContainer = Color(0xFF001F29),
    secondary = Color(0xFF4C616B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCFE6F2),
    onSecondaryContainer = Color(0xFF081E27),
    tertiary = Color(0xFF5B5B7E),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE1DFFF),
    onTertiaryContainer = Color(0xFF181837),
    background = Color(0xFFF6FAFD),
    onBackground = Color(0xFF171C1F),
    surface = Color(0xFFF6FAFD),
    onSurface = Color(0xFF171C1F),
    surfaceVariant = Color(0xFFDCE4E9),
    onSurfaceVariant = Color(0xFF40484C),
    outline = Color(0xFF70787D),
    outlineVariant = Color(0xFFC0C8CD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4F8),
    surfaceContainer = Color(0xFFEAEEF2),
    surfaceContainerHigh = Color(0xFFE4E9EC),
    surfaceContainerHighest = Color(0xFFDFE3E7),
)

internal val CorrienteDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF5DD5FC),
    onPrimary = Color(0xFF003545),
    primaryContainer = Color(0xFF004D62),
    onPrimaryContainer = Color(0xFFBCE9FF),
    secondary = Color(0xFFB3CAD6),
    onSecondary = Color(0xFF1E333C),
    secondaryContainer = Color(0xFF354A53),
    onSecondaryContainer = Color(0xFFCFE6F2),
    tertiary = Color(0xFFC3C2EA),
    onTertiary = Color(0xFF2C2C4D),
    tertiaryContainer = Color(0xFF434365),
    onTertiaryContainer = Color(0xFFE1DFFF),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFDEE3E7),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFDEE3E7),
    surfaceVariant = Color(0xFF40484C),
    onSurfaceVariant = Color(0xFFC0C8CD),
    outline = Color(0xFF8A9296),
    outlineVariant = Color(0xFF40484C),
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceContainerLow = Color(0xFF171C1F),
    surfaceContainer = Color(0xFF1B2023),
    surfaceContainerHigh = Color(0xFF252B2E),
    surfaceContainerHighest = Color(0xFF303639),
)

/** Семантические цвета денег: доход / расход. Пары для светлой и тёмной темы, не хардкод по экранам. */
data class CorrienteColors(
    val income: Color,
    val expense: Color,
)

internal val LightMoneyColors = CorrienteColors(income = Color(0xFF1B6E3A), expense = Color(0xFFB3261E))
internal val DarkMoneyColors = CorrienteColors(income = Color(0xFF7FD99A), expense = Color(0xFFFFB4AB))
