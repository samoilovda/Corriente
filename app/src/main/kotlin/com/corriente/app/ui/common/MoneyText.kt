package com.corriente.app.ui.common

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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

data class MoneyDisplayParts(val primary: String, val secondary: String)

/**
 * M-1: разделение отформатированной суммы на целую и дробную часть с валютой.
 * Примеры:
 * "-1 250.50 ₽" -> primary: "-1 250", secondary: ".50 ₽"
 * "125 ¥" -> primary: "125 ¥", secondary: ""
 * "1.234 KWD" -> primary: "1", secondary: ".234 KWD"
 * "1 000.00 ₽ → 10.00 $" -> primary: "1 000.00 ₽ → 10.00 $", secondary: "" (переводы не делятся)
 */
fun splitMoneyDisplay(formatted: String): MoneyDisplayParts {
    if (formatted.contains('→')) {
        return MoneyDisplayParts(primary = formatted, secondary = "")
    }
    val dotIndex = formatted.indexOf('.')
    if (dotIndex != -1 && dotIndex + 1 < formatted.length && formatted[dotIndex + 1].isDigit()) {
        return MoneyDisplayParts(
            primary = formatted.substring(0, dotIndex),
            secondary = formatted.substring(dotIndex),
        )
    }
    return MoneyDisplayParts(primary = formatted, secondary = "")
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
    /** M-1: приглушение дробной части и символа валюты для нео-финтех эстетики. */
    deemphasizeFraction: Boolean = false,
    /** U05: где важна полная сумма (шапка месяца), разрешаем перенос вместо «…». */
    maxLines: Int = 1,
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
    val mergedStyle = style.merge(MoneyTextStyle)
    if (deemphasizeFraction) {
        val parts = splitMoneyDisplay(text)
        if (parts.secondary.isNotEmpty()) {
            val annotated = buildAnnotatedString {
                append(parts.primary)
                withStyle(
                    SpanStyle(
                        color = resolved.copy(alpha = 0.68f),
                        fontWeight = FontWeight.Normal,
                    ),
                ) {
                    append(parts.secondary)
                }
            }
            Text(
                text = annotated,
                modifier = modifier.semantics { contentDescription = text },
                style = mergedStyle,
                color = resolved,
                textAlign = textAlign,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            return
        }
    }
    Text(
        text = text,
        modifier = modifier,
        style = mergedStyle,
        color = resolved,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

