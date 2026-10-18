package com.corriente.app.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.corriente.app.R

/**
 * T5.3: графики на Compose Canvas. В композиции — только уже посчитанные значения (Long/строки),
 * `Float` появляется исключительно на уровне координат отрисовки (ARCHITECTURE.md §2.1, I-1), доли —
 * через [ChartGeometry] (без переполнения Long).
 *
 * U08: сам Canvas скрыт от TalkBack — вместо «график» озвучиваются смысловые узлы: подпись с валютой и
 * периодом, по одному узлу на столбец/сегмент (их немного — до 7 месяцев и 6 категорий), и есть
 * видимая текстовая таблица значений.
 */

/**
 * U08: геометрия графиков на границе UI. Деньги остаются Long (I-1); здесь — только доли для координат.
 * Сумма и разности считаются в Double: `Long.sumOf` и `max - min` у очень больших значений переполнились
 * бы и молча развернули график (wraparound), а для пикселей точности Double достаточно.
 */
internal object ChartGeometry {

    /** Доли положительных значений от их суммы; отрицательные и нули — 0. Пустой/нулевой итог — все нули. */
    fun fractionsOfTotal(values: List<Long>): List<Double> {
        val total = values.sumOf { if (it > 0) it.toDouble() else 0.0 }
        if (total <= 0.0) return values.map { 0.0 }
        return values.map { if (it > 0) it.toDouble() / total else 0.0 }
    }

    /** Положение [value] между [min] и [max] в долях 0..1; вырожденный диапазон — 0. */
    fun normalized(value: Long, min: Long, max: Long): Double {
        val span = max.toDouble() - min.toDouble()
        if (span <= 0.0) return 0.0
        return ((value.toDouble() - min.toDouble()) / span).coerceIn(0.0, 1.0)
    }
}

internal val PALETTE = listOf(
    0xFF4E79A7, 0xFFF28E2B, 0xFFE15759, 0xFF76B7B2, 0xFF59A14F,
    0xFFEDC948, 0xFFB07AA1, 0xFFFF9DA7, 0xFF9C755F, 0xFFBAB0AC,
).map { Color(it) }

internal fun sliceColor(argb: Int, index: Int): Color =
    if (argb != 0) Color(argb) else PALETTE[index % PALETTE.size]

/** Сколько категорий в легенде кольца; остальные — строкой «ещё N» (полный список — под графиком в отчёте). */
private const val LEGEND_LIMIT = 6

@Composable
fun MonthlyBarChart(bars: List<MonthlyBar>, currencyCode: String, modifier: Modifier = Modifier) {
    if (bars.isEmpty()) return
    val maxValue = bars.maxOf { it.valueMinor }.coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    var valuesShown by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            stringResource(R.string.report_chart_monthly_in, currencyCode),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
        )
        Canvas(Modifier.fillMaxWidth().height(140.dp).clearAndSetSemantics { }) {
            val n = bars.size
            val gap = size.width / (n * 3f)
            val barWidth = (size.width - gap * (n + 1)) / n
            val baseline = size.height
            val cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            val minBarHeight = 4.dp.toPx()
            val totalH = size.height - 8.dp.toPx()

            drawLine(axisColor, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 2f)
            bars.forEachIndexed { i, bar ->
                val left = gap + i * (barWidth + gap)
                // Фоновый трек на всю высоту столбца
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, baseline - totalH),
                    size = Size(barWidth, totalH),
                    cornerRadius = cornerRadius,
                )
                if (bar.valueMinor > 0) {
                    val rawH = (ChartGeometry.normalized(bar.valueMinor, 0L, maxValue) * totalH).toFloat()
                    val h = rawH.coerceAtLeast(minBarHeight)
                    val isHighlight = i == bars.lastIndex || bar.valueMinor == maxValue
                    val fillCol = if (isHighlight) barColor else barColor.copy(alpha = 0.55f)
                    drawRoundRect(
                        color = fillCol,
                        topLeft = Offset(left, baseline - h),
                        size = Size(barWidth, h),
                        cornerRadius = cornerRadius,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            bars.forEach { bar ->
                Text(
                    bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "${bar.label}: ${bar.amountText}" },
                )
            }
        }
        TextButton(onClick = { valuesShown = !valuesShown }) {
            Text(stringResource(if (valuesShown) R.string.report_chart_values_hide else R.string.report_chart_values_show))
        }
        if (valuesShown) {
            bars.forEach { bar ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).semantics(mergeDescendants = true) {}) {
                    Text(bar.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(bar.amountText, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                }
            }
        }
    }
}

/**
 * R3.2: линия остатка счёта по дням за период. Значения уже посчитаны накопительным итогом
 * ([com.corriente.data.usecase.balanceSeries]) — здесь только координаты (`Float`, как и в
 * остальных графиках T5.3). Остаток может уходить в минус (овердрафт/долг) — ноль отмечен
 * отдельной линией, чтобы это было видно, а не терялось в масштабе графика.
 * U08: [description] — смысловая сводка (начало, конец, минимум, максимум) вместо безымянного Canvas.
 */
@Composable
fun AccountBalanceLineChart(points: List<Long>, description: String, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val min = points.min()
    val max = points.max()
    val lineColor = MaterialTheme.colorScheme.primary
    val zeroLineColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier.fillMaxWidth().height(140.dp).padding(vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        val padY = 8.dp.toPx()
        val availH = size.height - padY * 2f
        fun y(value: Long): Float = (size.height - padY) - (ChartGeometry.normalized(value, min, max) * availH).toFloat()

        if (min < 0 && max > 0) {
            val zeroY = y(0L)
            drawLine(zeroLineColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 2f)
        }

        val stepX = size.width / (points.size - 1)
        val strokePath = Path().apply {
            moveTo(0f, y(points[0]))
            for (i in 1 until points.size) {
                lineTo(i * stepX, y(points[i]))
            }
        }
        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo((points.size - 1) * stepX, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            fillPath,
            brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.24f), Color.Transparent)),
        )
        drawPath(
            strokePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        val lastIdx = points.size - 1
        drawCircle(
            color = lineColor,
            radius = 4.dp.toPx(),
            center = Offset(lastIdx * stepX, y(points[lastIdx])),
        )
    }
}

@Composable
fun CategoryDonut(slices: List<CategorySlice>, currencyCode: String, periodLabel: String, modifier: Modifier = Modifier) {
    val positive = slices.filter { it.valueMinor > 0 }
    if (positive.isEmpty()) return
    val fractions = ChartGeometry.fractionsOfTotal(positive.map { it.valueMinor })
    val noCategory = stringResource(R.string.report_no_category)
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            stringResource(R.string.report_chart_structure_in, currencyCode, periodLabel),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(120.dp).clearAndSetSemantics { }) {
                val stroke = size.minDimension / 5f
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val radius = (size.minDimension - stroke) / 2f
                val capDegrees = Math.toDegrees(kotlin.math.atan2((stroke / 2f).toDouble(), radius.toDouble())).toFloat()
                val gap = if (positive.size > 1) 4f else 0f

                // Фоновый трек кольца
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke * 0.65f),
                )

                var startAngle = -90f
                positive.forEachIndexed { i, slice ->
                    val fullSweep = (fractions[i] * 360.0).toFloat()
                    val color = sliceColor(slice.color, i)
                    if (positive.size == 1) {
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = fullSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt),
                        )
                    } else if (fullSweep > gap + capDegrees * 2f) {
                        drawArc(
                            color = color,
                            startAngle = startAngle + gap / 2f + capDegrees,
                            sweepAngle = fullSweep - gap - capDegrees * 2f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    } else {
                        val buttSweep = (fullSweep - gap).coerceAtLeast(1.5f)
                        drawArc(
                            color = color,
                            startAngle = startAngle + (fullSweep - buttSweep) / 2f,
                            sweepAngle = buttSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Butt),
                        )
                    }
                    startAngle += fullSweep
                }
            }
            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                positive.take(LEGEND_LIMIT).forEachIndexed { i, slice ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.semantics(mergeDescendants = true) {},
                    ) {
                        Canvas(Modifier.size(10.dp).clearAndSetSemantics { }) {
                            drawCircle(sliceColor(slice.color, i))
                        }
                        Text(
                            "  ${slice.name ?: noCategory} — ${slice.amountText}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            maxLines = 2,
                        )
                    }
                }
                if (positive.size > LEGEND_LIMIT) {
                    Text(
                        stringResource(R.string.report_chart_more, positive.size - LEGEND_LIMIT),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
