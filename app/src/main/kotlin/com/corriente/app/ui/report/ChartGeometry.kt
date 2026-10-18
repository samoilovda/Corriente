package com.corriente.app.ui.report

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
