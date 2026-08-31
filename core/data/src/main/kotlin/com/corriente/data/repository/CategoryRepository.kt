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

/**
 * Правила, которых нет в схеме Room и которые enforced здесь (T0.4, ARCHITECTURE.md §3.2):
 *  - одна степень вложенности: родитель категории сам не может иметь родителя;
 *  - подкатегория того же типа (EXPENSE/INCOME), что и родитель;
 *  - слияние — только категорий одного типа и только листовых (без подкатегорий), I-11 не затрагивает
 *    (переводы категорий не имеют вовсе).
 */
class CategoryRepository(private val dao: CategoryDao) {

    fun observeActive(): Flow<List<Category>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    fun observeArchived(): Flow<List<Category>> = dao.observeArchived().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Category? = dao.getById(id)?.toDomain()

    suspend fun hasTransactions(id: String): Boolean = dao.hasTransactions(id)

    suspend fun create(
        name: String,
        kind: CategoryKind,
        parentId: String? = null,
        color: Int,
        icon: String? = null,
        origin: CategoryOrigin = CategoryOrigin.USER,
    ): Category {
        if (parentId != null) requireValidParent(parentId, kind)
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

    suspend fun update(id: String, name: String, parentId: String?, color: Int, icon: String?) {
        val existing = requireNotNull(dao.getById(id)) { "Category $id not found" }
        if (parentId != null) {
            require(parentId != id) { "Category cannot be its own parent" }
            require(dao.childCount(id) == 0) { "A category with subcategories cannot itself become a subcategory" }
            requireValidParent(parentId, existing.kind)
        }
        dao.update(existing.copy(name = name, parentId = parentId, color = color, icon = icon))
    }

    suspend fun archive(id: String) {
        val existing = requireNotNull(dao.getById(id)) { "Category $id not found" }
        dao.update(existing.copy(isArchived = true))
    }

    suspend fun unarchive(id: String) {
        val existing = requireNotNull(dao.getById(id)) { "Category $id not found" }
        dao.update(existing.copy(isArchived = false))
    }

    /** Физическое удаление — только если категорией никто не пользуется (нет операций и подкатегорий). */
    suspend fun deleteIfUnused(id: String): Boolean {
        if (dao.hasTransactions(id) || dao.childCount(id) > 0) return false
        val existing = dao.getById(id) ?: return false
        dao.delete(existing)
        return true
    }

    /**
     * Слияние (T1.4): операции [fromId] переезжают на [intoId], исходная категория исчезает.
     * Только для категорий одного типа и только листовых — иначе подкатегории [fromId] осиротеют.
     */
    suspend fun mergeInto(fromId: String, intoId: String) {
        require(fromId != intoId) { "Cannot merge a category into itself" }
        val from = requireNotNull(dao.getById(fromId)) { "Category $fromId not found" }
        val into = requireNotNull(dao.getById(intoId)) { "Category $intoId not found" }
        require(from.kind == into.kind) { "Cannot merge ${from.kind} into ${into.kind}" }
        require(dao.childCount(fromId) == 0) { "Archive or move its subcategories first" }
        dao.merge(fromId, intoId)
    }

    private suspend fun requireValidParent(parentId: String, kind: CategoryKind) {
        val parent = requireNotNull(dao.getById(parentId)) { "Parent category $parentId not found" }
        require(parent.parentId == null) { "Only a top-level category can be a parent (one nesting level)" }
        require(parent.kind == kind) { "Subcategory must have the same kind as its parent" }
    }
}
