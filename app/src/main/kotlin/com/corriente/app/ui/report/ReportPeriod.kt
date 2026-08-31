package com.corriente.app.ui.report

import java.time.LocalDate

/** Как задан период отчёта (T1.8). CUSTOM — две произвольные даты. */
enum class PeriodMode { MONTH, QUARTER, YEAR, CUSTOM }

/**
 * Границы периода. Для MONTH/QUARTER/YEAR — вокруг [anchor]; для CUSTOM — [customStart]..[customEnd]
 * (порядок нормализуется). Всё в локальных датах, без времени и таймзон.
 */
fun periodRange(
    mode: PeriodMode,
    anchor: LocalDate,
    customStart: LocalDate = anchor,
    customEnd: LocalDate = anchor,
): ClosedRange<LocalDate> = when (mode) {
    PeriodMode.MONTH -> anchor.withDayOfMonth(1)..anchor.withDayOfMonth(anchor.lengthOfMonth())
    PeriodMode.QUARTER -> {
        val firstMonth = (anchor.monthValue - 1) / 3 * 3 + 1
        val start = LocalDate.of(anchor.year, firstMonth, 1)
        start..start.plusMonths(2).let { it.withDayOfMonth(it.lengthOfMonth()) }
    }
    PeriodMode.YEAR -> LocalDate.of(anchor.year, 1, 1)..LocalDate.of(anchor.year, 12, 31)
    PeriodMode.CUSTOM -> minOf(customStart, customEnd)..maxOf(customStart, customEnd)
}

/** Сдвиг опорной даты на [delta] периодов вперёд/назад (стрелки «‹ ›»). CUSTOM не двигается. */
fun shiftAnchor(mode: PeriodMode, anchor: LocalDate, delta: Long): LocalDate = when (mode) {
    PeriodMode.MONTH -> anchor.plusMonths(delta).withDayOfMonth(1)
    PeriodMode.QUARTER -> anchor.plusMonths(delta * 3).withDayOfMonth(1)
    PeriodMode.YEAR -> anchor.plusYears(delta).withDayOfMonth(1)
    PeriodMode.CUSTOM -> anchor
}

/** Человекочитаемая подпись периода для шапки экрана. */
fun periodLabel(mode: PeriodMode, range: ClosedRange<LocalDate>): String = when (mode) {
    PeriodMode.MONTH -> "%02d.%d".format(range.start.monthValue, range.start.year)
    PeriodMode.QUARTER -> "Q%d %d".format((range.start.monthValue - 1) / 3 + 1, range.start.year)
    PeriodMode.YEAR -> range.start.year.toString()
    PeriodMode.CUSTOM -> "${range.start} – ${range.endInclusive}"
}
