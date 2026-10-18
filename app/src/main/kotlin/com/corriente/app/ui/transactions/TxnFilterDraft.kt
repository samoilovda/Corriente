package com.corriente.app.ui.transactions

import com.corriente.money.AmountInput
import com.corriente.money.AmountParse
import com.corriente.money.AmountParseError
import com.corriente.money.Currency
import com.corriente.money.Minor
import com.corriente.money.StrictAmountParser
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

/**
 * U01: порог суммы всегда привязан к валюте — без неё «100» значит 100 RUB, 100 JPY или 0.100 KWD.
 * Сравниваются только стороны операции в [currencyCode]; конвертации нет. `null` у границы — её нет.
 */
data class AmountRange(
    val currencyCode: String,
    val minMinor: Long? = null,
    val maxMinor: Long? = null,
)

/** Ошибка одного поля листа фильтров; текст — забота экрана. */
enum class FilterFieldError {
    DATE_INVALID,
    PERIOD_REVERSED,
    AMOUNT_NOT_A_NUMBER,
    AMOUNT_NEGATIVE,
    AMOUNT_TOO_MANY_DECIMALS,
    AMOUNT_OVERFLOW,
    AMOUNT_MIN_GT_MAX,
}

data class FilterDraftErrors(
    val from: FilterFieldError? = null,
    val to: FilterFieldError? = null,
    val min: FilterFieldError? = null,
    val max: FilterFieldError? = null,
) {
    val any: Boolean get() = from != null || to != null || min != null || max != null
}

sealed interface FilterDraftCheck {
    data class Valid(val filter: TxnFilter) : FilterDraftCheck
    data class Invalid(val errors: FilterDraftErrors) : FilterDraftCheck
}

/**
 * U01/U04: редактор листа фильтров — исходный текст полей, выбранные счёт/валюта/категория. Чистая
 * модель: применяется к [TxnFilter] только целиком через [validate] — либо все параметры валидны,
 * либо фильтр остаётся прежним. Пустое поле — явное отсутствие границы, невалидное — ошибка, а не `null`.
 *
 * Валютный контекст суммы ([amountCurrencyCode]): валюта выбранного счёта → явно выбранная валюта →
 * валюта уже действующего порога (если ни счёт, ни валюта не выбраны). Без контекста поля суммы недоступны.
 */
data class TxnFilterDraft(
    val accountId: String? = null,
    val currencyCode: String? = null,
    val categoryId: String? = null,
    val fromText: String = "",
    val toText: String = "",
    val minText: String = "",
    val maxText: String = "",
    /** Валюта действующего порога — держит контекст, пока счёт/валюта не выбраны. */
    val stickyAmountCurrency: String? = null,
    /** Пороги только что сброшены сменой валюты — экран объясняет почему. */
    val amountResetByCurrencyChange: Boolean = false,
) {
    fun amountCurrencyCode(accountCurrencies: Map<String, String>): String? =
        effectiveCurrency(accountId, currencyCode, accountCurrencies) ?: stickyAmountCurrency

    fun withAccount(id: String?, accountCurrencies: Map<String, String>): TxnFilterDraft =
        withContext(copy(accountId = id), accountCurrencies)

    fun withCurrency(code: String?, accountCurrencies: Map<String, String>): TxnFilterDraft =
        withContext(copy(currencyCode = code), accountCurrencies)

    /** Новая валюта — пороги сбрасываются: старые цифры не переинтерпретируются в другой валюте. */
    private fun withContext(next: TxnFilterDraft, accountCurrencies: Map<String, String>): TxnFilterDraft {
        val before = amountCurrencyCode(accountCurrencies)
        val after = effectiveCurrency(next.accountId, next.currencyCode, accountCurrencies)
        // Счёт/валюта сняты — порог остаётся в своей валюте, как и снятие одного chip над списком.
        if (after == null) return next.copy(stickyAmountCurrency = before)
        if (after == before) return next
        val hadAmount = minText.isNotBlank() || maxText.isNotBlank()
        return next.copy(
            minText = "",
            maxText = "",
            stickyAmountCurrency = null,
            amountResetByCurrencyChange = hadAmount || amountResetByCurrencyChange,
        )
    }

    fun withPeriod(from: LocalDate?, to: LocalDate?): TxnFilterDraft =
        copy(fromText = from?.toString() ?: "", toText = to?.toString() ?: "")

    fun thisMonth(today: LocalDate): TxnFilterDraft = YearMonth.from(today).let { withPeriod(it.atDay(1), it.atEndOfMonth()) }

    fun lastMonth(today: LocalDate): TxnFilterDraft =
        YearMonth.from(today).minusMonths(1).let { withPeriod(it.atDay(1), it.atEndOfMonth()) }

    fun allDates(): TxnFilterDraft = withPeriod(null, null)

    /**
     * Итог листа: [FilterDraftCheck.Valid] с новым фильтром (запрос поиска [base] сохраняется) либо ошибки
     * по полям. [currencies] — справочник: точность разбора суммы — `minorUnits` валюты контекста.
     */
    fun validate(
        base: TxnFilter,
        accountCurrencies: Map<String, String>,
        currencies: Map<String, Currency>,
    ): FilterDraftCheck {
        val from = parseDate(fromText)
        val to = parseDate(toText)
        var errors = FilterDraftErrors(
            from = FilterFieldError.DATE_INVALID.takeIf { from == null },
            to = FilterFieldError.DATE_INVALID.takeIf { to == null },
        )
        val fromDate = from?.getOrNull()
        val toDate = to?.getOrNull()
        if (fromDate != null && toDate != null && fromDate > toDate) {
            errors = errors.copy(from = FilterFieldError.PERIOD_REVERSED)
        }

        val amountCode = amountCurrencyCode(accountCurrencies)
        val amountCurrency = amountCode?.let(currencies::get)
        var range: AmountRange? = null
        if (amountCurrency != null) {
            val min = StrictAmountParser.parse(minText, amountCurrency)
            val max = StrictAmountParser.parse(maxText, amountCurrency)
            errors = errors.copy(min = min.fieldError(), max = max.fieldError())
            val minValue = (min as? AmountParse.Value)?.minor?.raw
            val maxValue = (max as? AmountParse.Value)?.minor?.raw
            if (minValue != null && maxValue != null && minValue > maxValue) {
                errors = errors.copy(min = FilterFieldError.AMOUNT_MIN_GT_MAX)
            }
            if (minValue != null || maxValue != null) range = AmountRange(amountCode, minValue, maxValue)
        }
        // Без валютного контекста поля недоступны на экране; набранное раньше в них не применяется.

        if (errors.any) return FilterDraftCheck.Invalid(errors)
        return FilterDraftCheck.Valid(
            base.copy(
                accountId = accountId,
                currencyCode = currencyCode,
                categoryId = categoryId,
                from = fromDate,
                to = toDate,
                amount = range,
            ),
        )
    }

    companion object {
        /** Лист открывается с действующим фильтром: суммы показываются точно по `minorUnits` их валюты. */
        fun of(filter: TxnFilter, currencies: Map<String, Currency>): TxnFilterDraft {
            val amountCurrency = filter.amount?.let { currencies[it.currencyCode] }
            fun text(minor: Long?): String =
                if (minor == null || amountCurrency == null) "" else AmountInput.fromMinor(Minor(minor), amountCurrency).displayText()
            return TxnFilterDraft(
                accountId = filter.accountId,
                currencyCode = filter.currencyCode,
                categoryId = filter.categoryId,
                fromText = filter.from?.toString() ?: "",
                toText = filter.to?.toString() ?: "",
                minText = text(filter.amount?.minMinor),
                maxText = text(filter.amount?.maxMinor),
                stickyAmountCurrency = filter.amount?.currencyCode?.takeIf { amountCurrency != null },
            )
        }
    }
}

/** Валюта счёта важнее явно выбранной валюты: счёт однозначно задаёт валюту своих операций. */
internal fun effectiveCurrency(accountId: String?, currencyCode: String?, accountCurrencies: Map<String, String>): String? =
    accountId?.let(accountCurrencies::get) ?: currencyCode

/** `null` — ошибка; `Result` с `null` внутри — пустое поле (границы нет). ISO строгий: 2026-02-31 — ошибка. */
private fun parseDate(text: String): Result<LocalDate?>? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return Result.success(null)
    return try {
        Result.success(LocalDate.parse(trimmed))
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun AmountParse.fieldError(): FilterFieldError? = when (this) {
    AmountParse.Empty, is AmountParse.Value -> null
    is AmountParse.Invalid -> when (error) {
        AmountParseError.NOT_A_NUMBER -> FilterFieldError.AMOUNT_NOT_A_NUMBER
        AmountParseError.NEGATIVE -> FilterFieldError.AMOUNT_NEGATIVE
        AmountParseError.TOO_MANY_DECIMALS -> FilterFieldError.AMOUNT_TOO_MANY_DECIMALS
        AmountParseError.OVERFLOW -> FilterFieldError.AMOUNT_OVERFLOW
    }
}
