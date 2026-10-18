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
import androidx.compose.ui.platform.testTag
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
                    .testTag("ring-${icon.categoryId}")
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
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    val uncategorized = MaterialTheme.colorScheme.outline
    // Доли — из уже посчитанных Long; дробные числа здесь — только координаты отрисовки (I-1).
    val total = slices.sumOf { it.valueMinor }.toDouble()
    Canvas(modifier) {
        val strokeWidth = size.minDimension / 6.5f
        val stroke = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val inset = strokeWidth / 2f
        val arcSize = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)

        // Фоновый трек кольца
        drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = stroke)

        if (total <= 0.0) return@Canvas

        val positiveSlices = slices.filter { it.valueMinor > 0 }
        if (positiveSlices.isEmpty()) return@Canvas

        val gapDegrees = if (positiveSlices.size > 1) 4f else 0f
        val totalGaps = gapDegrees * positiveSlices.size
        val usableDegrees = (360f - totalGaps).coerceAtLeast(0f)

        var start = -90f
        positiveSlices.forEach { slice ->
            val rawSweep = (slice.valueMinor / total * usableDegrees).toFloat()
            val sweep = if (positiveSlices.size > 1) (rawSweep - 0.5f).coerceAtLeast(1f) else rawSweep
            drawArc(
                color = if (slice.color != 0) categoryColor(slice.color) else uncategorized,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            start += rawSweep + gapDegrees
        }
    }
}

