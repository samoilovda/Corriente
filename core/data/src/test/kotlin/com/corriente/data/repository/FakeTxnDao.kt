package com.corriente.data.repository

import com.corriente.data.db.dao.TxnDao
import com.corriente.data.db.entity.TxnEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeTxnDao : TxnDao {

    val rows = MutableStateFlow<List<TxnEntity>>(emptyList())

    override suspend fun insert(txn: TxnEntity) { rows.value = rows.value + txn }

    override suspend fun insertAll(txns: List<TxnEntity>) { rows.value = rows.value + txns }

    override suspend fun update(txn: TxnEntity) {
        rows.value = rows.value.map { if (it.id == txn.id) txn else it }
    }

    override suspend fun delete(txn: TxnEntity) {
        rows.value = rows.value.filterNot { it.id == txn.id }
    }

    override suspend fun getById(id: String): TxnEntity? = rows.value.firstOrNull { it.id == id }

    override fun observeForAccount(accountId: String): Flow<List<TxnEntity>> =
        rows.map { list -> list.filter { it.accountId == accountId || it.toAccountId == accountId } }

    override fun observeAll(): Flow<List<TxnEntity>> = rows

    override suspend fun countByImportHash(importHash: String): Int = rows.value.count { it.importHash == importHash }

    override suspend fun deleteByImportBatch(batchId: String) {
        rows.value = rows.value.filterNot { it.importBatchId == batchId }
    }

    override suspend fun deleteAll() { rows.value = emptyList() }
}
