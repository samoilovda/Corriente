package com.corriente.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corriente.app.ui.common.CategoryIconVectors
import com.corriente.data.category.materialIconKey
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Money
import com.corriente.money.MoneyFormatter
import java.time.LocalDate

val MONTHS = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)

val INCOME_COLOR = Color(0xFF43A047)
val EXPENSE_COLOR = Color(0xFFE53935)

private val FALLBACK = listOf(
    0xFF4E79A7, 0xFFF28E2B, 0xFFE15759, 0xFF76B7B2, 0xFF59A14F,
    0xFFEDC948, 0xFFB07AA1, 0xFFFF9DA7, 0xFF9C755F, 0xFFBAB0AC,
).map { Color(it) }

fun categoryColor(argb: Int, index: Int = 0): Color = if (argb != 0) Color(argb) else FALLBACK[index % FALLBACK.size]

/** Иконка категории — как на телефоне: линейная (`mi:<ключ>`), эмодзи или первая буква названия. */
@Composable
fun CategoryGlyph(icon: String?, name: String, color: Color, size: Dp = 20.dp) {
    val key = materialIconKey(icon)
    val vector = key?.let { CategoryIconVectors.byKey[it] }
    Box(Modifier.size(size + 4.dp), contentAlignment = Alignment.Center) {
        when {
            vector != null -> Icon(vector, contentDescription = null, tint = color, modifier = Modifier.size(size))
            key == null && !icon.isNullOrBlank() -> Text(icon, style = TextStyle(fontSize = (size.value * 0.9f).sp))
            else -> Text(name.trim().take(1).uppercase(), style = TextStyle(fontSize = (size.value * 0.8f).sp), color = color)
        }
    }
}

@Composable
fun CategoryBadge(icon: String?, name: String, argb: Int, size: Dp = 32.dp) {
    val color = categoryColor(argb)
    Box(Modifier.size(size).background(color.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
        CategoryGlyph(icon, name, color, size * 0.55f)
    }
}

fun currencyOf(code: CurrencyCode, byCode: Map<String, Currency>): Currency =
    byCode[code.code] ?: Currency(code, minorUnits = 2, displayScale = 2, symbol = code.code)

fun formatMoney(money: Money, byCode: Map<String, Currency>): String = MoneyFormatter.format(money, currencyOf(money.currency, byCode))

private val WEEKDAYS = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Сегодня"
    today.minusDays(1) -> "Вчера"
    else -> "%02d.%02d.%d, %s".format(java.util.Locale.ROOT, date.dayOfMonth, date.monthValue, date.year, WEEKDAYS[date.dayOfWeek.value - 1])
}
