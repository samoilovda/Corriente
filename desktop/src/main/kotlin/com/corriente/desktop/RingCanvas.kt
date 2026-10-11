package com.corriente.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.corriente.data.usecase.MonthOverview
import com.corriente.data.usecase.OverviewSlice
import kotlin.math.cos
import kotlin.math.sin

// Только отрисовка: дуги кольца и позиции иконок по окружности. Дробные числа здесь — координаты Canvas/offset;
// доли и проценты считает buildMonthOverview целыми числами (I-1). Файл — в floatAllowlist InvariantGuards.

private val RING = 150.dp
private val ICON = 38.dp
private val ORBIT = 130.dp

/** Кольцо расходов месяца с иконками категорий вокруг — как на телефоне; тап по иконке — расход в эту категорию. */
@Composable
fun RingWithIcons(overview: MonthOverview, onCategory: (String) -> Unit) {
    Box(Modifier.size(ORBIT * 2 + ICON + 36.dp), contentAlignment = Alignment.Center) {
        Ring(overview.slices, Modifier.size(RING))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(RING - 44.dp)) {
            Text(overview.incomeText, color = INCOME_COLOR, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(overview.expenseText, color = EXPENSE_COLOR, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        val n = overview.icons.size
        overview.icons.forEachIndexed { i, icon ->
            val angle = Math.toRadians(-90.0 + 360.0 * i / n)
            Column(
                Modifier
                    .offset(x = ORBIT * cos(angle).toFloat(), y = ORBIT * sin(angle).toFloat() + 8.dp)
                    .width(ICON + 24.dp)
                    .clickable { onCategory(icon.categoryId) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CategoryBadge(icon.icon, icon.name, icon.color, ICON)
                Text(icon.percent?.let { "$it%" } ?: " ", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun Ring(slices: List<OverviewSlice>, modifier: Modifier) {
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val uncategorized = MaterialTheme.colorScheme.outline
    // Доли — из уже посчитанных Long; дробные числа здесь — только координаты отрисовки (I-1).
    val total = slices.sumOf { it.valueMinor }.toDouble()
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension / 6.5f)
        val inset = stroke.width / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        if (total <= 0.0) {
            drawArc(empty, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)
            return@Canvas
        }
        var start = -90f
        slices.forEachIndexed { i, slice ->
            val sweep = (slice.valueMinor / total * 360.0).toFloat()
            drawArc(if (slice.color != 0) categoryColor(slice.color) else uncategorized, start, sweep, false, topLeft, arcSize, style = stroke)
            start += sweep
        }
    }
}

