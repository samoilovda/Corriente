package com.corriente.data.repository

import com.corriente.data.db.dao.CategoryDao
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CategoryOrigin
import com.corriente.data.model.Category
import com.corriente.data.model.toDomain
import com.corriente.data.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class CategoryRepository(private val dao: CategoryDao) {

    fun observeActive(): Flow<List<Category>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Category? = dao.getById(id)?.toDomain()

    suspend fun create(
        name: String,
        kind: CategoryKind,
        parentId: String? = null,
        color: Int,
        icon: String? = null,
        origin: CategoryOrigin = CategoryOrigin.USER,
    ): Category {
        val category = Category(
            id = UUID.randomUUID().toString(),
            name = name,
            kind = kind,
            parentId = parentId,
            color = color,
            icon = icon,
            origin = origin,
            displayOrder = 0,
            isArchived = false,
        )
        dao.insert(category.toEntity())
        return category
    }

    suspend fun rename(id: String, name: String, color: Int, icon: String?) {
        val existing = requireNotNull(dao.getById(id)) { "Category $id not found" }
        dao.update(existing.copy(name = name, color = color, icon = icon))
    }

    suspend fun archive(id: String) {
        val existing = requireNotNull(dao.getById(id)) { "Category $id not found" }
        dao.update(existing.copy(isArchived = true))
    }

    /** Переносит операции [fromId] на [intoId] и удаляет исходную категорию (T1.4). */
    suspend fun mergeInto(fromId: String, intoId: String) {
        require(fromId != intoId) { "Cannot merge a category into itself" }
        dao.reassignTransactions(fromId, intoId)
        val from = requireNotNull(dao.getById(fromId)) { "Category $fromId not found" }
        dao.delete(from)
    }
}
