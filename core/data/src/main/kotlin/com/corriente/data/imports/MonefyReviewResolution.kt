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

    /** [ReviewReason.ACCOUNT_CURRENCY_CONFLICT]: валюта счёта, выбранная вручную. */
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
                plainTxns = plainTxns + affected.flatMap { it.toUnpairedHalves() }
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
                accounts = accounts.map { if (it.name == name) it.copy(currency = decision.code) else it }
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

private fun PlannedTransfer.toUnpairedHalves(): List<PlannedTxn> = listOf(
    PlannedTxn(
        line = fromLine, date = date, account = fromAccount, category = UNPAIRED_TRANSFER_CATEGORY,
        amountMinor = fromAmountMinor, currency = fromCurrency, kind = MonefyTxnKind.EXPENSE,
        naturalKey = fromNaturalKey, unpairedHalf = true,
    ),
    PlannedTxn(
        line = toLine, date = date, account = toAccount, category = UNPAIRED_TRANSFER_CATEGORY,
        amountMinor = toAmountMinor, currency = toCurrency, kind = MonefyTxnKind.INCOME,
        naturalKey = toNaturalKey, unpairedHalf = true,
    ),
)
