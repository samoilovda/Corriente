package com.corriente.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/** Типографика M3 на системном шрифте; заголовки экранов чуть плотнее по весу. */
internal val CorrienteTypography = Typography().let {
    it.copy(
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.Medium),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.Medium),
    )
}

/** Стиль для сумм: табличные цифры (колонка не «пляшет»), средний вес. */
val MoneyTextStyle = TextStyle(
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum",
)
