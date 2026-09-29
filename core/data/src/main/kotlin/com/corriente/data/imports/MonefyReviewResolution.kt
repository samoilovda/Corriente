package com.corriente.data.imports

import com.corriente.money.CurrencyCode

/**
 * T3.3 (ранее в BACKLOG): ручное разрешение позиций NEEDS_REVIEW прямо на экране dry-run.
 * Планировщик ничего не «угадывает» — он лишь предлагает вариант; окончательное решение
 * принимает пользователь и оно применяется к плану ДО записи в БД.
 *
 * Всё здесь — чистые функции над [MonefyImportPlan]; БД и Android не участвуют, поведение
 * покрыто `MonefyReviewResolutionTest`.
 */

/** Ссылка на позицию NEEDS_REVIEW внутри плана — по причине и номерам строк CSV. */
data class ReviewRef(val reason: ReviewReason, val lines: List<Int>)

fun ReviewItem.ref(): ReviewRef = ReviewRef(reason, lines)

/** Решение пользователя по одной позиции NEEDS_REVIEW. */
sealed interface ReviewDecision {

    /** Согласиться с вариантом планировщика — снять пометку, ничего не менять. */
    data object Accept : ReviewDecision

    /**
     * [ReviewReason.AMBIGUOUS_PAIRING]: не склеивать — сохранить обе половинки как отдельные
     * операции с категорией [UNPAIRED_TRANSFER_CATEGORY] (пользователь свяжет их сам, если нужно).
     */
    data object KeepSeparate : ReviewDecision

    /**
     * [ReviewReason.ANOMALOUS_CURRENCY]: неявный курс 1.0 — это не курс. Считать перевод
     * одновалютным в валюте отправителя (сумма-приёмник = сумме-источнику).
     */
    data object SameCurrency : ReviewDecision

    /** [ReviewReason.EXCESS_PRECISION]: точные суммы перевода вместо округлённых планировщиком. */
    data class ExactAmounts(val fromMinor: Long, val toMinor: Long) : ReviewDecision

    /**
     * [ReviewReason.ACCOUNT_CURRENCY_CONFLICT] (A1.8): в счёте с исходным именем остаются строки валюты
     * [code]; строки остальных валют (включая стороны переводов) уходят в «<имя> (<валюта>)» — один счёт
     * не может держать две валюты (I-15).
     */
    data class AccountCurrency(val code: CurrencyCode) : ReviewDecision

    /**
     * [ReviewReason.EXISTING_ACCOUNT_CURRENCY_MISMATCH]: не трогать существующий счёт, а завести
     * отдельный «<имя> (<валюта из файла>)» и класть операции импорта в него.
     */
    data object SeparateAccount : ReviewDecision
}

/** Имя отдельного счёта для [ReviewDecision.SeparateAccount] — «Cash (USD)». */
fun separateAccountName(name: String, currency: CurrencyCode): String = "$name ($currency)"

/** Решения, допустимые для каждой причины (для построения экрана). */
fun allowedDecisions(reason: ReviewReason): List<String> = when (reason) {
    ReviewReason.AMBIGUOUS_PAIRING -> listOf("accept", "keep_separate")
    ReviewReason.ANOMALOUS_CURRENCY -> listOf("accept", "same_currency")
    ReviewReason.EXCESS_PRECISION -> listOf("accept", "exact_amounts")
    ReviewReason.ACCOUNT_CURRENCY_CONFLICT -> listOf("account_currency")
    ReviewReason.EXISTING_ACCOUNT_CURRENCY_MISMATCH -> listOf("separate_account")
}

/**
 * Применяет решения к плану и возвращает новый: затронутые переводы/счета переписаны,
 * разрешённые позиции убраны из [MonefyImportPlan.reviews]. Позиции без решения не трогаются —
 * импорт применит вариант планировщика (это работало и без экрана).
 */
fun MonefyImportPlan.applyReviewDecisions(decisions: Map<ReviewRef, ReviewDecision>): MonefyImportPlan {
    if (decisions.isEmpty()) return this

    var transfers = this.transfers
    var plainTxns = this.plainTxns
    var accounts = this.accounts
    val resolved = mutableSetOf<ReviewRef>()

    for (review in reviews) {
        val decision = decisions[review.ref()] ?: continue
        val affected = transfers.filter { it.fromLine in review.lines && it.toLine in review.lines }.toSet()

        when (decision) {
            ReviewDecision.Accept ->
                transfers = transfers.map { if (it in affected) it.copy(review = null) else it }

            ReviewDecision.KeepSeparate -> {
                transfers = transfers.filterNot { it in affected }
                plainTxns = plainTxns + affected.flatMap { it.toUnpairedHalves(unpairedCategory) }
            }

            ReviewDecision.SameCurrency ->
                transfers = transfers.map {
                    if (it in affected) {
                        it.copy(toCurrency = it.fromCurrency, toAmountMinor = it.fromAmountMinor, review = null)
                    } else {
                        it
                    }
                }

            is ReviewDecision.ExactAmounts ->
                transfers = transfers.map {
                    if (it in affected) {
                        it.copy(fromAmountMinor = decision.fromMinor, toAmountMinor = decision.toMinor, review = null)
                    } else {
                        it
                    }
                }

            is ReviewDecision.AccountCurrency -> {
                val name = review.account ?: continue
                val split = splitAccountByCurrency(name, decision.code, accounts, plainTxns, transfers)
                accounts = split.accounts
                plainTxns = split.plainTxns
                transfers = split.transfers
            }

            ReviewDecision.SeparateAccount -> {
                val name = review.account ?: continue
                val planned = accounts.firstOrNull { it.name == name } ?: continue
                val newName = separateAccountName(name, planned.currency)
                accounts = accounts.map { if (it.name == name) it.copy(name = newName) else it }
                plainTxns = plainTxns.map { if (it.account == name) it.copy(account = newName) else it }
                transfers = transfers.map {
                    it.copy(
                        fromAccount = if (it.fromAccount == name) newName else it.fromAccount,
                        toAccount = if (it.toAccount == name) newName else it.toAccount,
                    )
                }
            }
        }
        resolved += review.ref()
    }

    return copy(
        transfers = transfers,
        plainTxns = plainTxns,
        accounts = accounts,
        reviews = reviews.filterNot { it.ref() in resolved },
    )
}

private class SplitResult(
    val accounts: List<PlannedAccount>,
    val plainTxns: List<PlannedTxn>,
    val transfers: List<PlannedTransfer>,
)

/** A1.8: см. [ReviewDecision.AccountCurrency]. Начальный остаток уходит в счёт своей (первой встреченной) валюты. */
private fun splitAccountByCurrency(
    name: String,
    keep: CurrencyCode,
    accounts: List<PlannedAccount>,
    plainTxns: List<PlannedTxn>,
    transfers: List<PlannedTransfer>,
): SplitResult {
    val planned = accounts.firstOrNull { it.name == name } ?: return SplitResult(accounts, plainTxns, transfers)
    val otherCurrencies = LinkedHashSet<CurrencyCode>()
    plainTxns.filter { it.account == name && it.currency != keep }.forEach { otherCurrencies += it.currency }
    transfers.forEach {
        if (it.fromAccount == name && it.fromCurrency != keep) otherCurrencies += it.fromCurrency
        if (it.toAccount == name && it.toCurrency != keep) otherCurrencies += it.toCurrency
    }
    // Остаток записан в валюте planned.currency: если её оставляют — остаётся здесь, иначе переезжает.
    val openingStays = planned.currency == keep
    if (!openingStays) otherCurrencies += planned.currency

    val newAccounts = accounts.map {
        if (it.name == name) it.copy(currency = keep, openingBalanceMinor = if (openingStays) it.openingBalanceMinor else 0L) else it
    } + otherCurrencies.map { currency ->
        PlannedAccount(
            name = separateAccountName(name, currency),
            currency = currency,
            openingBalanceMinor = if (!openingStays && currency == planned.currency) planned.openingBalanceMinor else 0L,
        )
    }.filter { candidate -> accounts.none { it.name == candidate.name } }
    return SplitResult(
        accounts = newAccounts,
        plainTxns = plainTxns.map {
            if (it.account == name && it.currency != keep) it.copy(account = separateAccountName(name, it.currency)) else it
        },
        transfers = transfers.map {
            it.copy(
                fromAccount = if (it.fromAccount == name && it.fromCurrency != keep) separateAccountName(name, it.fromCurrency) else it.fromAccount,
                toAccount = if (it.toAccount == name && it.toCurrency != keep) separateAccountName(name, it.toCurrency) else it.toAccount,
            )
        },
    )
}

private fun PlannedTransfer.toUnpairedHalves(category: String): List<PlannedTxn> = listOf(
    PlannedTxn(
        line = fromLine, date = date, account = fromAccount, category = category,
        amountMinor = fromAmountMinor, currency = fromCurrency, kind = MonefyTxnKind.EXPENSE,
        naturalKey = fromNaturalKey, unpairedHalf = true,
    ),
    PlannedTxn(
        line = toLine, date = date, account = toAccount, category = category,
        amountMinor = toAmountMinor, currency = toCurrency, kind = MonefyTxnKind.INCOME,
        naturalKey = toNaturalKey, unpairedHalf = true,
    ),
)
