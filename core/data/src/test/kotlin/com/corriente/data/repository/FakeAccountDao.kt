package com.corriente.data.repository

import com.corriente.data.db.dao.AccountDao
import com.corriente.data.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Ручной [AccountDao] на in-memory списке. [accountsWithTransactions] моделирует наличие
 * операций по счёту — этого достаточно для проверки I-23 в [AccountRepository], настоящий
 * `txn` тут не нужен.
 */
class FakeAccountDao(
    private val accountsWithTransactions: MutableSet<String> = mutableSetOf(),
) : AccountDao {

    private val rows = MutableStateFlow<List<AccountEntity>>(emptyList())

    fun markHasTransactions(accountId: String) {
        accountsWithTransactions += accountId
    }

    override suspend fun insert(account: AccountEntity): Long {
        rows.value = rows.value + account
        return 1L
    }

    override suspend fun update(account: AccountEntity) {
        rows.value = rows.value.map { if (it.id == account.id) account else it }
    }

    override suspend fun delete(account: AccountEntity) {
        rows.value = rows.value.filterNot { it.id == account.id }
    }

    override fun observeActive(): Flow<List<AccountEntity>> =
        rows.map { list -> list.filterNot { it.isArchived }.sortedBy { it.displayOrder } }

    override fun observeArchived(): Flow<List<AccountEntity>> =
        rows.map { list -> list.filter { it.isArchived }.sortedBy { it.displayOrder } }

    override fun observeAll(): Flow<List<AccountEntity>> =
        rows.map { list -> list.sortedBy { it.displayOrder } }

    override suspend fun getById(id: String): AccountEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun hasTransactions(accountId: String): Boolean = accountId in accountsWithTransactions

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}
