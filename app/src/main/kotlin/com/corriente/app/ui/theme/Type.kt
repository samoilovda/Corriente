package com.corriente.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp


/** Типографика M3 Expressive на системном шрифте: плотные заголовки и табличные цифры для денег. */
internal val CorrienteTypography = Typography().let {
    it.copy(
        displaySmall = it.displaySmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5f).sp,
            fontFeatureSettings = "tnum",
        ),
        headlineLarge = it.headlineLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5f).sp,
            fontFeatureSettings = "tnum",
        ),
        headlineMedium = it.headlineMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.5f).sp,
            fontFeatureSettings = "tnum",
        ),
        headlineSmall = it.headlineSmall.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.25f).sp,
            fontFeatureSettings = "tnum",
        ),
        titleLarge = it.titleLarge.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.25f).sp,
        ),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.Medium),
        titleSmall = it.titleSmall.copy(fontWeight = FontWeight.Medium),
        labelLarge = it.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

/** Стиль для сумм: табличные цифры (колонка не «пляшет»), полужирный вес. */
val MoneyTextStyle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum",
)

