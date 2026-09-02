package com.corriente.data.recurrence

import java.time.LocalDate
import java.time.YearMonth

/** R2.4: правило повторения — конкретный день месяца или интервал в днях. */
sealed interface RecurrenceRule {
    /** [day] 1..31; в месяце короче — берётся последний день месяца (31 января → 28/29 февраля). */
    data class DayOfMonth(val day: Int) : RecurrenceRule {
        init { require(day in 1..31) { "day must be 1..31, was $day" } }
    }

    /** Каждые [intervalDays] дней от предыдущей операции. */
    data class EveryNDays(val intervalDays: Int) : RecurrenceRule {
        init { require(intervalDays >= 1) { "intervalDays must be >= 1, was $intervalDays" } }
    }
}

/**
 * Следующая дата после [from] по правилу [rule]. Для [RecurrenceRule.DayOfMonth] это всегда
 * следующий календарный месяц (даже если [rule].day не совпадает с днём [from] — предыдущая
 * дата могла быть клэмпнута коротким месяцем, а этот расчёт всегда опирается на исходный
 * [RecurrenceRule.DayOfMonth.day], а не на то, чем он стал после клэмпинга, поэтому 31-е после
 * февраля снова становится 31-м в марте, а не застревает на 28-м).
 */
fun advance(rule: RecurrenceRule, from: LocalDate): LocalDate = when (rule) {
    is RecurrenceRule.EveryNDays -> from.plusDays(rule.intervalDays.toLong())
    is RecurrenceRule.DayOfMonth -> {
        val nextMonth = YearMonth.from(from).plusMonths(1)
        nextMonth.atDay(rule.day.coerceAtMost(nextMonth.lengthOfMonth()))
    }
}

/**
 * Первая дата правила, приходящаяся на [from] или позже — используется при создании нового
 * правила ("начать с сегодня"), чтобы [nextRunOn] с самого начала был валидной датой по
 * [rule], а не произвольной [from].
 */
fun firstOccurrenceOnOrAfter(rule: RecurrenceRule, from: LocalDate): LocalDate = when (rule) {
    is RecurrenceRule.EveryNDays -> from
    is RecurrenceRule.DayOfMonth -> {
        val thisMonth = YearMonth.from(from)
        val candidate = thisMonth.atDay(rule.day.coerceAtMost(thisMonth.lengthOfMonth()))
        if (!candidate.isBefore(from)) candidate else advance(rule, candidate)
    }
}

/**
 * R2.4 — чистая функция: все даты, за которые пора материализовать операцию, от [nextRunOn]
 * (включительно) до [today] (включительно), в порядке возрастания. Пусто, если [nextRunOn] в
 * будущем — операции создаются только за прошедшие/сегодняшние даты, никогда авансом.
 *
 * Пропуск нескольких периодов (устройство долго было выключено/дата переведена вперёд) даёт
 * несколько дат за один вызов — ровно по одной на каждый пропущенный период, не одну "догоняющую".
 */
fun dueDates(rule: RecurrenceRule, nextRunOn: LocalDate, today: LocalDate): List<LocalDate> {
    val dates = mutableListOf<LocalDate>()
    var cursor = nextRunOn
    while (!cursor.isAfter(today)) {
        dates += cursor
        cursor = advance(rule, cursor)
    }
    return dates
}
