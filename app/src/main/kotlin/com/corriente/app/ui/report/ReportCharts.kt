package com.corriente.app.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

/**
 * T5.3: графики на Compose Canvas. В композиции — только уже посчитанные значения (Long/строки),
 * `Float` появляется исключительно на уровне координат отрисовки (ARCHITECTURE.md §2.1, I-1).
 */

private val PALETTE = listOf(
    0xFF4E79A7, 0xFFF28E2B, 0xFFE15759, 0xFF76B7B2, 0xFF59A14F,
    0xFFEDC948, 0xFFB07AA1, 0xFFFF9DA7, 0xFF9C755F, 0xFFBAB0AC,
).map { Color(it) }

private fun sliceColor(argb: Int, index: Int): Color =
    if (argb != 0) Color(argb) else PALETTE[index % PALETTE.size]

@Composable
fun MonthlyBarChart(bars: List<MonthlyBar>, modifier: Modifier = Modifier) {
    if (bars.isEmpty()) return
    val maxValue = bars.maxOf { it.valueMinor }.coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            stringResourceMonthly(),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val n = bars.size
            val gap = size.width / (n * 3f)
            val barWidth = (size.width - gap * (n + 1)) / n
            val baseline = size.height
            drawLine(axisColor, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 2f)
            bars.forEachIndexed { i, bar ->
                val h = (bar.valueMinor.toDouble() / maxValue.toDouble() * (size.height - 8f)).toFloat()
                val left = gap + i * (barWidth + gap)
                drawRect(
                    color = barColor,
                    topLeft = Offset(left, baseline - h),
                    size = Size(barWidth, h),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            bars.forEach { Text(it.label, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

/**
 * R3.2: линия остатка счёта по дням за период. Значения уже посчитаны накопительным итогом
 * ([com.corriente.data.usecase.balanceSeries]) — здесь только координаты (`Float`, как и в
 * остальных графиках T5.3). Остаток может уходить в минус (овердрафт/долг) — ноль отмечен
 * отдельной линией, чтобы это было видно, а не терялось в масштабе графика.
 */
@Composable
fun AccountBalanceLineChart(points: List<Long>, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val min = points.min()
    val max = points.max()
    val span = (max - min).coerceAtLeast(1L)
    val lineColor = MaterialTheme.colorScheme.primary
    val zeroLineColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier.fillMaxWidth().height(140.dp).padding(vertical = 4.dp)) {
        fun y(value: Long): Float =
            size.height - ((value - min).toDouble() / span.toDouble() * size.height).toFloat()

        if (min < 0 && max > 0) {
            val zeroY = y(0L)
            drawLine(zeroLineColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 2f)
        }

        val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f
        for (i in 0 until points.size - 1) {
            drawLine(
                color = lineColor,
                start = Offset(i * stepX, y(points[i])),
                end = Offset((i + 1) * stepX, y(points[i + 1])),
                strokeWidth = 4f,
            )
        }
    }
}

@Composable
fun CategoryDonut(slices: List<CategorySlice>, modifier: Modifier = Modifier) {
    val positive = slices.filter { it.valueMinor > 0 }
    if (positive.isEmpty()) return
    val total = positive.sumOf { it.valueMinor }.toDouble()

    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            stringResourceStructure(),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Canvas(Modifier.size(120.dp)) {
                var startAngle = -90f
                positive.forEachIndexed { i, slice ->
                    val sweep = (slice.valueMinor / total * 360.0).toFloat()
                    drawArc(
                        color = sliceColor(slice.color, i),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = size.minDimension / 4f),
                    )
                    startAngle += sweep
                }
            }
            Column(Modifier.padding(start = 16.dp)) {
                positive.take(6).forEachIndexed { i, slice ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Canvas(Modifier.size(10.dp).padding(end = 0.dp)) {
                            drawCircle(sliceColor(slice.color, i))
                        }
                        Text(
                            "  ${slice.name} — ${slice.amountText}",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun stringResourceMonthly() =
    androidx.compose.ui.res.stringResource(com.corriente.app.R.string.report_chart_monthly)

@Composable
private fun stringResourceStructure() =
    androidx.compose.ui.res.stringResource(com.corriente.app.R.string.report_chart_structure)
