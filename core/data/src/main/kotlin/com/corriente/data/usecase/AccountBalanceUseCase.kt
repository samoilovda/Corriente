package com.corriente.data.usecase

import com.corriente.data.model.Account
import com.corriente.data.model.Txn
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class AccountBalance(val account: Account, val balance: Money)

/**
 * Баланс счёта = его остаток "на начало" плюс всё, что через него прошло (T1.7).
 * Всегда в валюте счёта — сложение с чем-либо ещё запрещено на уровне типов [Money] (I-2, I-8):
 * складывать балансы разных валют этот код физически не может, только вызывающий явно решает,
 * что показывать рядом (ADR-012 — списком по валютам, не одной суммой).
 */
fun accountBalance(account: Account, transactions: List<Txn>): Money {
    var balance = account.openingBalance
    for (txn in transactions) {
        balance = applyToBalance(balance, account, txn)
    }
    return balance
}

/**
 * Один шаг накопления баланса — вынесен из [accountBalance], чтобы [balanceSeries] (R3.2)
 * считал тот же баланс день за днём той же логикой, а не дублировал ветвление по [Txn].
 */
internal fun applyToBalance(balance: Money, account: Account, txn: Txn): Money = when (txn) {
    is Txn.Income -> if (txn.accountId == account.id) balance + txn.amount else balance
    is Txn.Expense -> if (txn.accountId == account.id) balance - txn.amount else balance
    is Txn.Transfer -> when (account.id) {
        txn.fromAccountId -> balance - txn.fromAmount
        txn.toAccountId -> balance + txn.toAmount
        else -> balance
    }
}

/** Сумма по валюте среди активных счетов, помеченных include_in_total (главный экран, T1.7). */
fun totalsByCurrency(balances: List<AccountBalance>): Map<CurrencyCode, Money> =
    balances
        .filter { it.account.includeInTotal }
        .groupBy { it.account.currency }
        .mapValues { (_, group) -> group.map { it.balance }.reduce { a, b -> a + b } }

class AccountBalanceUseCase(
    private val accountRepository: AccountRepository,
    private val txnRepository: TxnRepository,
) {
    /**
     * F2.1: баланс = `opening + delta`, где `delta` — агрегат из SQL. Раньше на каждую эмиссию
     * перегонялась вся таблица `txn` и для каждого счёта проходилась целиком (O(счета × операции)).
     * Чистая [accountBalance] осталась — на ней держатся тесты и офлайн-снимок виджета.
     */
    fun observeBalances(): Flow<List<AccountBalance>> =
        combine(accountRepository.observeActive(), txnRepository.observeAccountDeltas()) { accounts, deltas ->
            accounts.map { account ->
                val delta = deltas[account.id] ?: 0L
                val raw = Math.addExact(account.openingBalance.amount.raw, delta) // I-3
                AccountBalance(account, Money(Minor(raw), account.currency))
            }
        }

    fun observeTotalsByCurrency(): Flow<Map<CurrencyCode, Money>> =
        observeBalances().map { totalsByCurrency(it) }
}
