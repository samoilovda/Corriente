package com.corriente.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import com.corriente.app.R
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/*
 * UI-1.2: одни правила отображения дат на всех экранах. ISO-вид (`2026-10-17`) остаётся только в
 * файлах (CSV, бэкап, синк) — пользователю показываются эти форматы.
 */

/** «Сегодня», «Вчера», «пт, 17 октября»; для другого года — «17 октября 2025». */
fun formatDayHeader(
    date: LocalDate,
    today: LocalDate,
    locale: Locale,
    todayLabel: String,
    yesterdayLabel: String,
): String = when {
    date == today -> todayLabel
    date == today.minusDays(1) -> yesterdayLabel
    date.year == today.year -> DateTimeFormatter.ofPattern("EEE, d MMMM", locale).format(date)
    else -> DateTimeFormatter.ofPattern("d MMMM yyyy", locale).format(date)
}

/** Короткая дата без дня недели: «17 окт» в текущем году, «17 окт 2025» в другом. */
fun formatShortDate(date: LocalDate, today: LocalDate, locale: Locale): String {
    val pattern = if (date.year == today.year) "d MMM" else "d MMM yyyy"
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

/** «Октябрь 2026» — именительный падеж (standalone-форма), с заглавной буквы. */
fun formatMonth(month: YearMonth, locale: Locale): String =
    DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(month)
        .replaceFirstChar { it.titlecase(locale) }

/** Дата с годом для границ произвольного периода: «1 янв. 2026». */
fun formatMediumDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

@Composable
fun dayLabel(date: LocalDate): String = formatDayHeader(
    date = date,
    today = LocalDate.now(),
    locale = LocalLocale.current.platformLocale,
    todayLabel = stringResource(R.string.date_today),
    yesterdayLabel = stringResource(R.string.date_yesterday),
)

fun shortDateLabel(date: LocalDate): String = formatShortDate(date, LocalDate.now(), Locale.getDefault())
