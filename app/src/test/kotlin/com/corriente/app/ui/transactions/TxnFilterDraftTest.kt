package com.corriente.app.ui.transactions

import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** U01/U04: черновик листа фильтров — точный ввод сумм по валюте и атомарное применение. */
class TxnFilterDraftTest {

    private fun currency(code: String, minor: Int) = Currency(CurrencyCode(code), minor, minor, code)
    private val currencies = listOf(
        currency("RUB", 2), currency("JPY", 0), currency("KWD", 3), currency("CLF", 4), currency("USD", 2),
    ).associateBy { it.code.code }
    private val accountCurrencies = mapOf("rub1" to "RUB", "rub2" to "RUB", "yen" to "JPY", "kwd" to "KWD")
    private val base = TxnFilter(query = "кофе")

    private fun validate(draft: TxnFilterDraft) = draft.validate(base, accountCurrencies, currencies)
    private fun valid(draft: TxnFilterDraft): TxnFilter = (validate(draft) as FilterDraftCheck.Valid).filter
    private fun errors(draft: TxnFilterDraft): FilterDraftErrors = (validate(draft) as FilterDraftCheck.Invalid).errors

    @Test
    fun `amounts are parsed with the precision of the context currency`() {
        assertEquals(
            AmountRange("RUB", 12_550, null),
            valid(TxnFilterDraft(accountId = "rub1", minText = "125.50")).amount,
        )
        assertEquals(AmountRange("JPY", null, 125), valid(TxnFilterDraft(accountId = "yen", maxText = "125")).amount)
        assertEquals(AmountRange("KWD", 1_234, null), valid(TxnFilterDraft(accountId = "kwd", minText = "1.234")).amount)
        assertEquals(AmountRange("CLF", 12_345, null), valid(TxnFilterDraft(currencyCode = "CLF", minText = "1.2345")).amount)
    }

    @Test
    fun `account currency wins over the explicitly chosen currency`() {
        val draft = TxnFilterDraft(accountId = "yen", currencyCode = "RUB", minText = "125")
        assertEquals("JPY", draft.amountCurrencyCode(accountCurrencies))
        assertEquals(AmountRange("JPY", 125, null), valid(draft).amount)
    }

    @Test
    fun `without a currency context the amount is not applied`() {
        val draft = TxnFilterDraft(minText = "100")
        assertNull(draft.amountCurrencyCode(accountCurrencies))
        assertNull(valid(draft).amount)
    }

    @Test
    fun `invalid amounts are errors, not missing bounds`() {
        assertEquals(FilterFieldError.AMOUNT_TOO_MANY_DECIMALS, errors(TxnFilterDraft(accountId = "rub1", minText = "1.505")).min)
        assertEquals(FilterFieldError.AMOUNT_TOO_MANY_DECIMALS, errors(TxnFilterDraft(accountId = "yen", maxText = "1.5")).max)
        assertEquals(FilterFieldError.AMOUNT_NEGATIVE, errors(TxnFilterDraft(accountId = "rub1", minText = "-1")).min)
        assertEquals(FilterFieldError.AMOUNT_NOT_A_NUMBER, errors(TxnFilterDraft(accountId = "rub1", minText = "1O0")).min)
        assertEquals(
            FilterFieldError.AMOUNT_OVERFLOW,
            errors(TxnFilterDraft(accountId = "yen", maxText = "9223372036854775808")).max,
        )
        assertEquals(
            AmountRange("JPY", null, Long.MAX_VALUE),
            valid(TxnFilterDraft(accountId = "yen", maxText = Long.MAX_VALUE.toString())).amount,
        )
    }

    @Test
    fun `min greater than max is an error`() {
        assertEquals(FilterFieldError.AMOUNT_MIN_GT_MAX, errors(TxnFilterDraft(accountId = "rub1", minText = "10", maxText = "9.99")).min)
        assertEquals(AmountRange("RUB", 1_000, 1_000), valid(TxnFilterDraft(accountId = "rub1", minText = "10", maxText = "10")).amount)
    }

    @Test
    fun `dates are strict ISO, February 31 and reversed periods are errors`() {
        assertEquals(FilterFieldError.DATE_INVALID, errors(TxnFilterDraft(fromText = "2026-02-31")).from)
        assertEquals(FilterFieldError.DATE_INVALID, errors(TxnFilterDraft(toText = "31.03.2026")).to)
        assertEquals(FilterFieldError.PERIOD_REVERSED, errors(TxnFilterDraft(fromText = "2026-03-10", toText = "2026-03-01")).from)
        val open = valid(TxnFilterDraft(fromText = "2026-03-01", toText = ""))
        assertEquals(LocalDate.of(2026, 3, 1), open.from)
        assertNull(open.to)
    }

    @Test
    fun `valid draft replaces the whole filter but keeps the search query`() {
        val filter = valid(
            TxnFilterDraft(accountId = "rub1", categoryId = "food", fromText = "2026-03-01", toText = "2026-03-31", maxText = "500"),
        )
        assertEquals(
            TxnFilter(
                accountId = "rub1", query = "кофе", categoryId = "food",
                from = LocalDate.of(2026, 3, 1), to = LocalDate.of(2026, 3, 31), amount = AmountRange("RUB", null, 50_000),
            ),
            filter,
        )
    }

    @Test
    fun `one invalid field rejects the whole draft`() {
        val check = validate(TxnFilterDraft(accountId = "rub1", categoryId = "food", fromText = "2026-02-30", minText = "5"))
        assertTrue(check is FilterDraftCheck.Invalid)
    }

    @Test
    fun `changing to another currency clears thresholds with a notice`() {
        val draft = TxnFilterDraft(accountId = "rub1", minText = "100").withAccount("yen", accountCurrencies)
        assertEquals("", draft.minText)
        assertTrue(draft.amountResetByCurrencyChange)
    }

    @Test
    fun `same currency on another account keeps thresholds`() {
        val draft = TxnFilterDraft(accountId = "rub1", minText = "100").withAccount("rub2", accountCurrencies)
        assertEquals("100", draft.minText)
        assertFalse(draft.amountResetByCurrencyChange)
    }

    @Test
    fun `removing account keeps the threshold in its currency`() {
        val draft = TxnFilterDraft(accountId = "kwd", minText = "1.234").withAccount(null, accountCurrencies)
        assertEquals("KWD", draft.amountCurrencyCode(accountCurrencies))
        assertEquals(AmountRange("KWD", 1_234, null), valid(draft).amount)
    }

    @Test
    fun `draft reopens with exact text of the stored thresholds`() {
        val draft = TxnFilterDraft.of(TxnFilter(amount = AmountRange("KWD", 1_234, 2_000)), currencies)
        assertEquals("1.234", draft.minText)
        assertEquals("2", draft.maxText)
        assertEquals(AmountRange("KWD", 1_234, 2_000), draft.validate(TxnFilter(), accountCurrencies, currencies).let {
            (it as FilterDraftCheck.Valid).filter.amount
        })
    }

    @Test
    fun `presets set calendar months`() {
        val today = LocalDate.of(2024, 3, 15)
        assertEquals(TxnFilterDraft(fromText = "2024-03-01", toText = "2024-03-31"), TxnFilterDraft().thisMonth(today))
        assertEquals(TxnFilterDraft(fromText = "2024-02-01", toText = "2024-02-29"), TxnFilterDraft().lastMonth(today))
        assertEquals(TxnFilterDraft(), TxnFilterDraft(fromText = "2024-02-01", toText = "x").allDates())
    }
}
