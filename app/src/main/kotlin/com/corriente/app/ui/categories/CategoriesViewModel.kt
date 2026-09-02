package com.corriente.app.ui.categories

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.app.ui.common.WritingViewModel
import com.corriente.app.ui.common.looksLikeConstraintViolation
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.model.Category
import com.corriente.data.repository.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Категория верхнего уровня со своими подкатегориями (одна степень вложенности). */
data class CategoryNode(val category: Category, val children: List<Category>)

data class CategoriesUiState(
    val expense: List<CategoryNode> = emptyList(),
    val income: List<CategoryNode> = emptyList(),
    val archived: List<Category> = emptyList(),
)

data class CategoryForm(
    val name: String,
    val kind: CategoryKind,
    val parentId: String?,
    val color: Int,
    val icon: String?,
)

data class CategoryEditor(
    val editingId: String?,
    val name: String,
    val kind: CategoryKind,
    val parentId: String?,
    val color: Int,
    val icon: String,
    /** true — у категории есть подкатегории: её нельзя сделать подкатегорией и нельзя слить. */
    val hasChildren: Boolean,
)

/** Кандидат на слияние: активная категория того же типа, без подкатегорий, не сама [from]. */
data class MergeRequest(val from: Category, val candidates: List<Category>)

/** Плоский активный список → две ветки (расход/доход) с вложенностью в один уровень. */
internal fun buildBranches(active: List<Category>, kind: CategoryKind): List<CategoryNode> {
    val ofKind = active.filter { it.kind == kind }
    val childrenByParent = ofKind.filter { it.parentId != null }.groupBy { it.parentId }
    return ofKind.filter { it.parentId == null }
        .map { top -> CategoryNode(top, childrenByParent[top.id].orEmpty()) }
}

class CategoriesViewModel(private val repository: CategoryRepository) : WritingViewModel() {

    val uiState: StateFlow<CategoriesUiState> =
        combine(repository.observeActive(), repository.observeArchived()) { active, archived ->
            CategoriesUiState(
                expense = buildBranches(active, CategoryKind.EXPENSE),
                income = buildBranches(active, CategoryKind.INCOME),
                archived = archived,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    private val _editor = MutableStateFlow<CategoryEditor?>(null)
    val editor: StateFlow<CategoryEditor?> = _editor

    private val _merge = MutableStateFlow<MergeRequest?>(null)
    val merge: StateFlow<MergeRequest?> = _merge

    fun startCreate(kind: CategoryKind) {
        _editor.value = CategoryEditor(
            editingId = null,
            name = "",
            kind = kind,
            parentId = null,
            color = CategoryPalette.first(),
            icon = "",
            hasChildren = false,
        )
    }

    fun startEdit(category: Category) {
        viewModelScope.launch {
            _editor.value = CategoryEditor(
                editingId = category.id,
                name = category.name,
                kind = category.kind,
                parentId = category.parentId,
                color = category.color,
                icon = category.icon.orEmpty(),
                hasChildren = branchesContainChild(category.id),
            )
        }
    }

    fun closeEditor() {
        _editor.value = null
    }

    fun save(form: CategoryForm): Boolean {
        if (form.name.isBlank()) return false
        val editor = _editor.value
        val name = form.name.trim()
        launchWrite(
            onError = { e ->
                if (e.looksLikeConstraintViolation()) "Категория «$name» уже есть" else "Не удалось сохранить категорию"
            },
            onSuccess = { _editor.value = null },
        ) {
            val icon = form.icon?.trim()?.ifBlank { null }
            val id = editor?.editingId
            if (id == null) {
                repository.create(name, form.kind, form.parentId, form.color, icon)
            } else {
                repository.update(id, name, form.parentId, form.color, icon)
            }
        }
        return true
    }

    fun archive(id: String) {
        launchWrite(onError = { "Не удалось заархивировать категорию" }) { repository.archive(id) }
    }

    fun unarchive(id: String) {
        launchWrite(onError = { "Не удалось вернуть категорию из архива" }) { repository.unarchive(id) }
    }

    fun deleteIfUnused(id: String) {
        launchWrite(onError = { "Не удалось удалить категорию" }) { repository.deleteIfUnused(id) }
    }

    /**
     * Открывает выбор категории-приёмника для [from]: любые активные того же типа, кроме самой
     * [from]. Ограничение «источник без подкатегорий» проверяет [CategoryRepository.mergeInto].
     */
    fun startMerge(from: Category) {
        val branches = when (from.kind) {
            CategoryKind.EXPENSE -> uiState.value.expense
            CategoryKind.INCOME -> uiState.value.income
        }
        val candidates = branches.flatMap { listOf(it.category) + it.children }.filter { it.id != from.id }
        _editor.value = null
        _merge.value = MergeRequest(from, candidates)
    }

    fun confirmMerge(intoId: String) {
        val request = _merge.value ?: return
        launchWrite(
            onError = { "Не удалось слить категории" },
            onSuccess = { _merge.value = null },
        ) {
            repository.mergeInto(request.from.id, intoId)
        }
    }

    fun cancelMerge() {
        _merge.value = null
    }

    private fun branchesContainChild(parentId: String): Boolean {
        val state = uiState.value
        return (state.expense + state.income).any { it.category.id == parentId && it.children.isNotEmpty() }
    }

    companion object {
        fun factory(repository: CategoryRepository) = viewModelFactory {
            initializer { CategoriesViewModel(repository) }
        }
    }
}

/** Небольшая фиксированная палитра для категорий (ARGB). Полноценный подбор цвета — этап 5. */
val CategoryPalette: List<Int> = listOf(
    0xFFEF5350.toInt(), 0xFFAB47BC.toInt(), 0xFF5C6BC0.toInt(), 0xFF29B6F6.toInt(),
    0xFF26A69A.toInt(), 0xFF9CCC65.toInt(), 0xFFFFCA28.toInt(), 0xFF8D6E63.toInt(),
)
