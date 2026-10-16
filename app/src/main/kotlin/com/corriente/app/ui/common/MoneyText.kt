package com.corriente.app.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.corriente.app.ui.theme.LocalCorrienteColors
import com.corriente.app.ui.theme.MoneyTextStyle

/** UI-1.3: что за сумма — от этого зависит цвет. Знак и текст уже в строке (форматирует `MoneyFormatter`, I-25). */
enum class MoneyKind { EXPENSE, INCOME, TRANSFER, BALANCE }

/** Смысловой тон суммы; в цвет превращается в [MoneyText] через тему. */
enum class MoneyTone { INCOME, NEUTRAL, MUTED, NEGATIVE }

/**
 * Расход — обычный цвет текста (это норма, а не тревога), доход — зелёный, перевод — приглушённый,
 * остаток красный только когда он отрицательный.
 */
fun moneyTone(kind: MoneyKind, isNegativeBalance: Boolean = false): MoneyTone = when (kind) {
    MoneyKind.INCOME -> MoneyTone.INCOME
    MoneyKind.EXPENSE -> MoneyTone.NEUTRAL
    MoneyKind.TRANSFER -> MoneyTone.MUTED
    MoneyKind.BALANCE -> if (isNegativeBalance) MoneyTone.NEGATIVE else MoneyTone.NEUTRAL
}

@Composable
fun MoneyText(
    text: String,
    kind: MoneyKind,
    modifier: Modifier = Modifier,
    isNegativeBalance: Boolean = false,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    val tone = moneyTone(kind, isNegativeBalance)
    val resolved = if (color != Color.Unspecified) {
        color
    } else {
        when (tone) {
            MoneyTone.INCOME -> LocalCorrienteColors.current.income
            MoneyTone.NEGATIVE -> LocalCorrienteColors.current.expense
            MoneyTone.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
            MoneyTone.NEUTRAL -> MaterialTheme.colorScheme.onSurface
        }
    }
    Text(
        text = text,
        modifier = modifier,
        style = style.merge(MoneyTextStyle),
        color = resolved,
        textAlign = textAlign,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
