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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

private val PALETTE = listOf(
    0xFF4E79A7, 0xFFF28E2B, 0xFFE15759, 0xFF76B7B2, 0xFF59A14F,
    0xFFEDC948, 0xFFB07AA1, 0xFFFF9DA7, 0xFF9C755F, 0xFFBAB0AC,
).map { Color(it) }

private fun sliceColor(argb: Int, index: Int): Color =
    if (argb != 0) Color(argb) else PALETTE[index % PALETTE.size]

/** Сколько категорий в легенде кольца; остальные — строкой «ещё N» (полный список — под графиком в отчёте). */
private const val LEGEND_LIMIT = 6

@Composable
fun MonthlyBarChart(bars: List<MonthlyBar>, currencyCode: String, modifier: Modifier = Modifier) {
    if (bars.isEmpty()) return
    val maxValue = bars.maxOf { it.valueMinor }.coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.primary
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
            drawLine(axisColor, Offset(0f, baseline), Offset(size.width, baseline), strokeWidth = 2f)
            bars.forEachIndexed { i, bar ->
                val h = (ChartGeometry.normalized(bar.valueMinor, 0L, maxValue) * (size.height - 8f)).toFloat()
                val left = gap + i * (barWidth + gap)
                drawRect(
                    color = barColor,
                    topLeft = Offset(left, baseline - h),
                    size = Size(barWidth, h),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
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
        fun y(value: Long): Float = size.height - (ChartGeometry.normalized(value, min, max) * size.height).toFloat()

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
fun CategoryDonut(slices: List<CategorySlice>, currencyCode: String, periodLabel: String, modifier: Modifier = Modifier) {
    val positive = slices.filter { it.valueMinor > 0 }
    if (positive.isEmpty()) return
    val fractions = ChartGeometry.fractionsOfTotal(positive.map { it.valueMinor })
    val noCategory = stringResource(R.string.report_no_category)

    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            stringResource(R.string.report_chart_structure_in, currencyCode, periodLabel),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(120.dp).clearAndSetSemantics { }) {
                var startAngle = -90f
                positive.forEachIndexed { i, slice ->
                    val sweep = (fractions[i] * 360.0).toFloat()
                    // Обводка центрирована на контуре дуги: без отступа в полтолщины кольцо вылезает за холст.
                    val stroke = size.minDimension / 4f
                    drawArc(
                        color = sliceColor(slice.color, i),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(width = stroke),
                    )
                    startAngle += sweep
                }
            }
            Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                            style = MaterialTheme.typography.labelSmall,
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
