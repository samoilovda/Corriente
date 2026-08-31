package com.corriente.app.ui.categories

import com.corriente.data.db.dao.CategoryDao
import com.corriente.data.db.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [CategoryDao]. [txnCategory] моделирует привязку операций к категориям
 * (txnId -> categoryId) — этого хватает для проверки слияния и `hasTransactions`.
 */
class FakeCategoryDao : CategoryDao() {

    private val rows = MutableStateFlow<List<CategoryEntity>>(emptyList())
    private val txnCategory = mutableMapOf<String, String>()

    fun attachTransaction(txnId: String, categoryId: String) {
        txnCategory[txnId] = categoryId
    }

    fun transactionsOf(categoryId: String): Set<String> =
        txnCategory.filterValues { it == categoryId }.keys

    override suspend fun insert(category: CategoryEntity): Long {
        rows.value = rows.value + category
        return 1L
    }

    override suspend fun update(category: CategoryEntity) {
        rows.value = rows.value.map { if (it.id == category.id) category else it }
    }

    override suspend fun delete(category: CategoryEntity) {
        rows.value = rows.value.filterNot { it.id == category.id }
    }

    override fun observeActive(): Flow<List<CategoryEntity>> =
        rows.map { list -> list.filterNot { it.isArchived }.sortedBy { it.displayOrder } }

    override fun observeArchived(): Flow<List<CategoryEntity>> =
        rows.map { list -> list.filter { it.isArchived }.sortedBy { it.displayOrder } }

    override fun observeAll(): Flow<List<CategoryEntity>> =
        rows.map { list -> list.sortedBy { it.displayOrder } }

    override suspend fun getById(id: String): CategoryEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun hasTransactions(categoryId: String): Boolean = txnCategory.containsValue(categoryId)

    override suspend fun childCount(parentId: String): Int = rows.value.count { it.parentId == parentId }

    override suspend fun reassignTransactions(fromId: String, intoId: String) {
        txnCategory.keys.filter { txnCategory[it] == fromId }.forEach { txnCategory[it] = intoId }
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
        txnCategory.clear()
    }
}
