package com.corriente.data.imports

import com.corriente.data.seed.ISO_CURRENCIES
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.MonefyAmountParser
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Одна разобранная строка CSV Monefy (docs/MONEFY_IMPORT.md §1–2). Разбор — строго по индексам
 * колонок (в заголовке две колонки `currency`), числа — по правилу «запятая = тысячи, точка =
 * дробь», без учёта локали (I-25).
 */
data class MonefyRow(
    val line: Int,
    val date: LocalDate,
    val account: String,
    val rawCategory: String,
    /** Исходная строка суммы (колонка 3) — часть натурального ключа для идемпотентности импорта. */
    val amountText: String,
    /** Сумма в [currency], минорные единицы. Знак сохранён: `< 0` — расход, `> 0` — доход. */
    val amount: Minor,
    val currency: CurrencyCode,
    /** |converted amount| в минорных единицах [baseCurrency] — единственная общая база для склейки пар. */
    val convertedAbs: Long,
    val baseCurrency: CurrencyCode,
    val note: String?,
    /** true — в `amount` были лишние ненулевые дробные знаки, округлены (→ NEEDS_REVIEW у planner). */
    val amountRoundedFromExcess: Boolean,
)

/**
 * Почему строка не разобралась — структурно, а не готовой русской фразой (R6.3, ROADMAP.md §8):
 * текст для экрана строит вызывающий (`ImportScreen.kt`) через `stringResource`, чтобы
 * английская локаль тоже получала понятную ошибку, а не русский текст из core/data.
 */
sealed interface MonefyRowIssue {
    data class WrongFieldCount(val expected: Int, val actual: Int) : MonefyRowIssue
    data class UnparseableDate(val raw: String) : MonefyRowIssue
    /** [detail] — сообщение [MonefyAmountParser], уже на английском (не требует локализации). */
    data class UnparseableAmount(val raw: String, val detail: String?) : MonefyRowIssue
    data class UnparseableConvertedAmount(val raw: String, val detail: String?) : MonefyRowIssue
}

data class MonefyRowError(val line: Int, val raw: String, val issue: MonefyRowIssue)

data class MonefyCsvResult(val rows: List<MonefyRow>, val errors: List<MonefyRowError>)

object MonefyCsvParser {

    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private val isoMinorUnits: Map<String, Int> = ISO_CURRENCIES.associate { it.code to it.minorUnits }

    private fun currencyOf(code: String): Currency {
        val minor = isoMinorUnits[code] ?: 2
        return Currency(CurrencyCode(code), minorUnits = minor, displayScale = minor, symbol = code)
    }

    /**
     * Разбирает весь текст экспорта. Непарсящиеся строки идут в [MonefyCsvResult.errors]
     * (импорт продолжается — MONEFY_IMPORT.md §5 п.1–2), а не роняют разбор целиком.
     */
    fun parse(csv: String): MonefyCsvResult {
        val records = csvReader().readAll(csv)
        val rows = mutableListOf<MonefyRow>()
        val errors = mutableListOf<MonefyRowError>()

        records.forEachIndexed { index, fields ->
            val line = index + 1
            if (index == 0) return@forEachIndexed // заголовок
            if (fields.all { it.isBlank() }) return@forEachIndexed
            val raw = fields.joinToString(",")
            if (fields.size != 8) {
                errors += MonefyRowError(line, raw, MonefyRowIssue.WrongFieldCount(8, fields.size))
                return@forEachIndexed
            }
            val date = runCatching { LocalDate.parse(fields[0].trim(), DATE) }.getOrNull()
            if (date == null) {
                errors += MonefyRowError(line, raw, MonefyRowIssue.UnparseableDate(fields[0]))
                return@forEachIndexed
            }
            val currency = CurrencyCode(fields[4].trim())
            val baseCurrency = CurrencyCode(fields[6].trim())
            val amount = runCatching { MonefyAmountParser.parseLenient(fields[3], currencyOf(currency.code)) }
                .getOrElse {
                    errors += MonefyRowError(line, raw, MonefyRowIssue.UnparseableAmount(fields[3], it.message))
                    return@forEachIndexed
                }
            val converted = runCatching { MonefyAmountParser.parseLenient(fields[5], currencyOf(baseCurrency.code)) }
                .getOrElse {
                    errors += MonefyRowError(line, raw, MonefyRowIssue.UnparseableConvertedAmount(fields[5], it.message))
                    return@forEachIndexed
                }

            rows += MonefyRow(
                line = line,
                date = date,
                account = fields[1].trim(),
                rawCategory = fields[2].trim(),
                amountText = fields[3].trim(),
                amount = amount.minor,
                currency = currency,
                convertedAbs = kotlin.math.abs(converted.minor.raw),
                baseCurrency = baseCurrency,
                note = fields[7].trim().ifBlank { null },
                amountRoundedFromExcess = amount.roundedFromExcessPrecision,
            )
        }
        return MonefyCsvResult(rows, errors)
    }
}
