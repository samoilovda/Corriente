package com.corriente.data.repository

import com.corriente.data.db.dao.AccountDao
import com.corriente.data.db.dao.TxnDao
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.model.Txn
import com.corriente.data.model.toDomain
import com.corriente.data.model.toEntity
import com.corriente.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/**
 * Ядро денежной логики приложения. Здесь и только здесь enforced инварианты, которые Room
 * не проверяет на уровне схемы (T0.4, ARCHITECTURE.md §3.2):
 *  - I-1: amount всегда положителен, знак операции определяется её видом ([Txn.Expense]/
 *    [Txn.Income]/[Txn.Transfer]), а не значением суммы.
 *  - I-15/I-23: валюта операции равна валюте счёта на момент записи.
 *  - I-11: у перевода нет категории, у расхода/дохода нет второго счёта — гарантировано типом
 *    [Txn], здесь дополнительно нечего проверять.
 *  - I-7а: курс перевода — производная от двух сумм, здесь не хранится и не запрашивается.
 */
class TxnRepository(
    private val txnDao: TxnDao,
    private val accountDao: AccountDao,
) {
    fun observeAll(): Flow<List<Txn>> = txnDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeForAccount(accountId: String): Flow<List<Txn>> =
        txnDao.observeForAccount(accountId).map { list -> list.map { it.toDomain() } }

    suspend fun addExpense(accountId: String, amount: Money, categoryId: String?, date: LocalDate, note: String? = null): Txn {
        requireAmountMatchesAccount(accountId, amount)
        val now = System.currentTimeMillis()
        val txn = Txn.Expense(
            id = UUID.randomUUID().toString(),
            date = date,
            createdAt = now,
            updatedAt = now,
            accountId = accountId,
            amount = amount,
            categoryId = categoryId,
            note = note,
        )
        txnDao.insert(txn.toEntity())
        return txn
    }

    suspend fun addIncome(accountId: String, amount: Money, categoryId: String?, date: LocalDate, note: String? = null): Txn {
        requireAmountMatchesAccount(accountId, amount)
        val now = System.currentTimeMillis()
        val txn = Txn.Income(
            id = UUID.randomUUID().toString(),
            date = date,
            createdAt = now,
            updatedAt = now,
            accountId = accountId,
            amount = amount,
            categoryId = categoryId,
            note = note,
        )
        txnDao.insert(txn.toEntity())
        return txn
    }

    /**
     * Перевод между счетами, в том числе между валютами (T2.1). [fromAmount]/[toAmount] —
     * единственный источник истины; неявный курс сделки = toAmount / fromAmount, вычисляется
     * на слое ViewModel/UI для показа, здесь не хранится (I-7а, I-12).
     */
    suspend fun addTransfer(
        fromAccountId: String,
        fromAmount: Money,
        toAccountId: String,
        toAmount: Money,
        date: LocalDate,
        note: String? = null,
    ): Txn {
        require(fromAccountId != toAccountId) { "Cannot transfer an account to itself" }
        require(fromAmount.isPositive) { "Transfer fromAmount must be positive" }
        require(toAmount.isPositive) { "Transfer toAmount must be positive" }
        requireAmountMatchesAccount(fromAccountId, fromAmount)
        requireAmountMatchesAccount(toAccountId, toAmount)
        val now = System.currentTimeMillis()
        val txn = Txn.Transfer(
            id = UUID.randomUUID().toString(),
            date = date,
            createdAt = now,
            updatedAt = now,
            fromAccountId = fromAccountId,
            fromAmount = fromAmount,
            toAccountId = toAccountId,
            toAmount = toAmount,
            note = note,
        )
        txnDao.insert(txn.toEntity())
        return txn
    }

    /**
     * Правка перевода (T2.1): те же правила, что при создании — счета разные, обе суммы > 0,
     * валюты равны валютам счетов. Курс сделки по-прежнему нигде не хранится (I-7а/I-12).
     */
    suspend fun updateTransfer(
        id: String,
        fromAccountId: String,
        fromAmount: Money,
        toAccountId: String,
        toAmount: Money,
        date: LocalDate,
        note: String?,
    ): Txn {
        val existing = requireNotNull(txnDao.getById(id)) { "Transaction $id not found" }
        require(existing.kind == TxnKind.TRANSFER) { "$id is not a transfer" }
        require(fromAccountId != toAccountId) { "Cannot transfer an account to itself" }
        require(fromAmount.isPositive) { "Transfer fromAmount must be positive" }
        require(toAmount.isPositive) { "Transfer toAmount must be positive" }
        requireAmountMatchesAccount(fromAccountId, fromAmount)
        requireAmountMatchesAccount(toAccountId, toAmount)
        val updated = existing.copy(
            accountId = fromAccountId,
            amountMinor = fromAmount.amount.raw,
            currencyCode = fromAmount.currency.code,
            toAccountId = toAccountId,
            toAmountMinor = toAmount.amount.raw,
            toCurrencyCode = toAmount.currency.code,
            date = date.toString(),
            note = note,
            updatedAt = System.currentTimeMillis(),
        )
        txnDao.update(updated)
        return updated.toDomain()
    }

    suspend fun getById(id: String): Txn? = txnDao.getById(id)?.toDomain()

    /**
     * Правка расхода/дохода (T1.6): сумма, категория, дата, заметка, счёт. Валюта суммы обязана
     * совпадать с валютой нового счёта (I-15) — при смене счёта на другую валюту вызывающий
     * обязан заново ввести сумму. Перевод этим методом не редактируется (T2.x).
     */
    suspend fun updateEntry(
        id: String,
        accountId: String,
        amount: Money,
        categoryId: String?,
        date: LocalDate,
        note: String?,
    ): Txn {
        val existing = requireNotNull(txnDao.getById(id)) { "Transaction $id not found" }
        require(existing.kind != TxnKind.TRANSFER) { "Transfers are not editable here" }
        requireAmountMatchesAccount(accountId, amount)
        val updated = existing.copy(
            accountId = accountId,
            amountMinor = amount.amount.raw,
            currencyCode = amount.currency.code,
            categoryId = categoryId,
            date = date.toString(),
            note = note,
            updatedAt = System.currentTimeMillis(),
        )
        txnDao.update(updated)
        return updated.toDomain()
    }

    suspend fun delete(txn: Txn) {
        txnDao.delete(txn.toEntity())
    }

    suspend fun deleteById(id: String) {
        txnDao.getById(id)?.let { txnDao.delete(it) }
    }

    private suspend fun requireAmountMatchesAccount(accountId: String, amount: Money) {
        require(amount.isPositive) { "Transaction amount must be positive (sign comes from kind, I-1)" }
        val account = requireNotNull(accountDao.getById(accountId)) { "Account $accountId not found" }
        require(amount.currency.code == account.currencyCode) {
            "Amount currency ${amount.currency} does not match account currency ${account.currencyCode} (I-15)"
        }
    }
}
