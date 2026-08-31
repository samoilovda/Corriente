package com.corriente.app.ui.accounts

import com.corriente.data.db.dao.AccountDao
import com.corriente.data.db.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [AccountDao] для теста ViewModel. [withTransactions] моделирует I-23. */
class FakeAccountDao(private val withTransactions: MutableSet<String> = mutableSetOf()) : AccountDao {

    private val rows = MutableStateFlow<List<AccountEntity>>(emptyList())

    fun markHasTransactions(id: String) {
        withTransactions += id
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

    override suspend fun getById(id: String): AccountEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun hasTransactions(accountId: String): Boolean = accountId in withTransactions

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}
