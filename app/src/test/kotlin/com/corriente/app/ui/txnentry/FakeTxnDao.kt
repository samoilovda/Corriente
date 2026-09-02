package com.corriente.app.ui.txnentry

import com.corriente.data.db.dao.AccountDeltaRow
import com.corriente.data.db.dao.TxnDao
import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [TxnDao] для теста ViewModel ввода операции. */
class FakeTxnDao : TxnDao {

    val rows = MutableStateFlow<List<TxnEntity>>(emptyList())

    /** Установить, чтобы записи бросали — для тестов обработки ошибок записи (F0.2). */
    var failWith: Throwable? = null

    override suspend fun insert(txn: TxnEntity) {
        failWith?.let { throw it }
        rows.value = rows.value + txn
    }

    override suspend fun insertAll(txns: List<TxnEntity>) {
        rows.value = rows.value + txns
    }

    override suspend fun update(txn: TxnEntity) {
        failWith?.let { throw it }
        rows.value = rows.value.map { if (it.id == txn.id) txn else it }
    }

    override suspend fun delete(txn: TxnEntity) {
        failWith?.let { throw it }
        rows.value = rows.value.filterNot { it.id == txn.id }
    }

    override fun observeForAccount(accountId: String): Flow<List<TxnEntity>> =
        rows.map { list -> list.filter { it.accountId == accountId || it.toAccountId == accountId } }

    override fun observeAll(): Flow<List<TxnEntity>> = rows

    override fun observeRange(from: String, to: String): Flow<List<TxnEntity>> =
        rows.map { list -> list.filter { it.date >= from && it.date <= to } }

    override fun observeAnyExist(): Flow<Boolean> = rows.map { it.isNotEmpty() }

    override fun observeAccountDeltas(): Flow<List<AccountDeltaRow>> = rows.map { list ->
        val acc = HashMap<Pair<String, String>, Long>()
        list.forEach { t ->
            val signed = when (t.kind) {
                TxnKind.INCOME -> t.amountMinor
                TxnKind.EXPENSE, TxnKind.TRANSFER -> -t.amountMinor
            }
            acc.merge(t.accountId to t.currencyCode, signed) { a, b -> a + b }
            if (t.kind == TxnKind.TRANSFER) {
                acc.merge(t.toAccountId!! to t.toCurrencyCode!!, t.toAmountMinor!!) { a, b -> a + b }
            }
        }
        acc.map { (k, v) -> AccountDeltaRow(k.first, k.second, v) }
    }

    override suspend fun getById(id: String): TxnEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun countByImportHash(importHash: String): Int = rows.value.count { it.importHash == importHash }

    override suspend fun deleteByImportBatch(batchId: String) {
        rows.value = rows.value.filterNot { it.importBatchId == batchId }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}
