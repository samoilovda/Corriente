package com.corriente.data.usecase

/**
 * R3.1: изменение суммы категории к тому же периоду прошлого месяца/года — «Еда 32 400 ₽,
 * +18 % к августу». Чистая функция: сравнивает уже посчитанные отчёты по одной и той же
 * валюте (I-8 — вызывающий обязан построить оба [CategoryTotal] через [categoryReport] с
 * одной и той же `CurrencyCode`, сравнение чужих валют сюда не подаётся вовсе).
 *
 * Возвращает процент изменения только для категорий, присутствующих в [current] (строки
 * текущего отчёта) — категория, исчезнувшая в этом периоде, просто не появляется в [current]
 * и, соответственно, не имеет строки, для которой нужно было бы что-то сравнивать.
 * `null` в значении карты означает «данных за предыдущий период нет или они нулевые» —
 * экран обязан показать прочерк, а не 0 % (критерий приёмки R3.1).
 */
fun periodOverPeriodChange(
    current: List<CategoryTotal>,
    previous: List<CategoryTotal>,
): Map<String?, Int?> {
    val previousByCategory = previous.associate { it.categoryId to it.total.amount.raw }
    return current.associate { c ->
        val previousRaw = previousByCategory[c.categoryId]
        c.categoryId to changePercent(currentRaw = c.total.amount.raw, previousRaw = previousRaw)
    }
}

/**
 * Процент изменения `current` относительно `previous`, округлённый к нулю (целая часть).
 * `null` — деление на ноль (нет предыдущего периода или там был строгий ноль): 0 % считать
 * нельзя, это другая по смыслу величина («не изменилось», а не «сравнивать не с чем»).
 * I-3: умножение и вычитание — через `Math.*Exact`, деление на `|previousRaw|` переполниться
 * не может (делитель не отрицателен и не равен нулю на этой ветке).
 */
internal fun changePercent(currentRaw: Long, previousRaw: Long?): Int? {
    if (previousRaw == null || previousRaw == 0L) return null
    val diff = Math.subtractExact(currentRaw, previousRaw)
    val scaled = Math.multiplyExact(diff, 100L)
    val denominator = if (previousRaw == Long.MIN_VALUE) return null else kotlin.math.abs(previousRaw)
    return (scaled / denominator).toInt()
}
