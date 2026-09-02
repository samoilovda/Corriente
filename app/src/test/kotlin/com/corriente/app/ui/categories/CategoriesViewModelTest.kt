package com.corriente.app.ui.categories

import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.model.Category
import com.corriente.data.db.entity.CategoryOrigin
import com.corriente.data.repository.CategoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun build(): Pair<CategoriesViewModel, FakeCategoryDao> {
        val dao = FakeCategoryDao()
        return CategoriesViewModel(CategoryRepository(dao)) to dao
    }

    private fun CoroutineScope.observe(vm: CategoriesViewModel) {
        launch { vm.uiState.collect {} }
        launch { vm.editor.collect {} }
        launch { vm.merge.collect {} }
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // --- чистая функция ---

    @Test
    fun `buildBranches nests children under their top-level parent and filters by kind`() {
        fun cat(id: String, kind: CategoryKind, parent: String?) =
            Category(id, id, kind, parent, 0, null, CategoryOrigin.USER, 0, false)

        val active = listOf(
            cat("food", CategoryKind.EXPENSE, null),
            cat("cafe", CategoryKind.EXPENSE, "food"),
            cat("salary", CategoryKind.INCOME, null),
        )
        val expense = buildBranches(active, CategoryKind.EXPENSE)
        assertEquals(listOf("food"), expense.map { it.category.id })
        assertEquals(listOf("cafe"), expense.single().children.map { it.id })
        assertEquals(listOf("salary"), buildBranches(active, CategoryKind.INCOME).map { it.category.id })
    }

    // --- ViewModel ---

    @Test
    fun `create adds a top-level category to the right branch`() = runTest(dispatcher) {
        val (vm, _) = build()
        backgroundScope.observe(vm)
        vm.startCreate(CategoryKind.EXPENSE)
        advanceUntilIdle()
        assertTrue(vm.save(CategoryForm("Еда", CategoryKind.EXPENSE, null, 1, null)))
        advanceUntilIdle()
        assertEquals(listOf("Еда"), vm.uiState.value.expense.map { it.category.name })
        assertNull(vm.editor.value)
    }

    // F0.2 — дубликат имени раньше ронял приложение SQLiteConstraintException.
    @Test
    fun `a constraint failure on save surfaces a friendly message and keeps the editor open`() = runTest(dispatcher) {
        val (vm, dao) = build()
        backgroundScope.observe(vm)
        backgroundScope.launch { vm.messages.collect {} }
        dao.failInsertWith = RuntimeException("UNIQUE constraint failed: category.name")
        vm.startCreate(CategoryKind.EXPENSE)
        advanceUntilIdle()
        assertTrue(vm.save(CategoryForm("Еда", CategoryKind.EXPENSE, null, 1, null)))
        advanceUntilIdle()

        assertEquals("Категория «Еда» уже есть", vm.messages.value?.text)
        assertNotNull(vm.editor.value)
        assertTrue(vm.uiState.value.expense.isEmpty())
    }

    @Test
    fun `a subcategory shows up nested under its parent`() = runTest(dispatcher) {
        val (vm, _) = build()
        backgroundScope.observe(vm)
        vm.startCreate(CategoryKind.EXPENSE)
        advanceUntilIdle()
        vm.save(CategoryForm("Еда", CategoryKind.EXPENSE, null, 1, null))
        advanceUntilIdle()
        val parentId = vm.uiState.value.expense.single().category.id

        vm.startCreate(CategoryKind.EXPENSE)
        advanceUntilIdle()
        vm.save(CategoryForm("Кафе", CategoryKind.EXPENSE, parentId, 2, null))
        advanceUntilIdle()

        assertEquals(listOf("Кафе"), vm.uiState.value.expense.single().children.map { it.name })
    }

    @Test
    fun `merge moves transactions and removes the source category`() = runTest(dispatcher) {
        val (vm, dao) = build()
        backgroundScope.observe(vm)
        vm.startCreate(CategoryKind.EXPENSE)
        advanceUntilIdle()
        vm.save(CategoryForm("Еда", CategoryKind.EXPENSE, null, 1, null))
        advanceUntilIdle()
        vm.startCreate(CategoryKind.EXPENSE)
        advanceUntilIdle()
        vm.save(CategoryForm("Продукты", CategoryKind.EXPENSE, null, 2, null))
        advanceUntilIdle()

        val food = vm.uiState.value.expense.first { it.category.name == "Еда" }.category
        val groceries = vm.uiState.value.expense.first { it.category.name == "Продукты" }.category
        dao.attachTransaction("t1", food.id)

        vm.startMerge(food)
        advanceUntilIdle()
        assertEquals(listOf("Продукты"), vm.merge.value!!.candidates.map { it.name })
        vm.confirmMerge(groceries.id)
        advanceUntilIdle()

        assertEquals(listOf("Продукты"), vm.uiState.value.expense.map { it.category.name })
        assertEquals(setOf("t1"), dao.transactionsOf(groceries.id))
        assertNull(vm.merge.value)
    }

    @Test
    fun `archive then unarchive moves the category between lists`() = runTest(dispatcher) {
        val (vm, _) = build()
        backgroundScope.observe(vm)
        vm.startCreate(CategoryKind.INCOME)
        advanceUntilIdle()
        vm.save(CategoryForm("Подарки", CategoryKind.INCOME, null, 1, null))
        advanceUntilIdle()
        val id = vm.uiState.value.income.single().category.id

        vm.archive(id)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.income.isEmpty())
        assertEquals(listOf(id), vm.uiState.value.archived.map { it.id })

        vm.unarchive(id)
        advanceUntilIdle()
        assertEquals(listOf(id), vm.uiState.value.income.map { it.category.id })
    }

    @Test
    fun `save rejects a blank name`() = runTest(dispatcher) {
        val (vm, _) = build()
        backgroundScope.observe(vm)
        vm.startCreate(CategoryKind.EXPENSE)
        advanceUntilIdle()
        assertFalse(vm.save(CategoryForm("   ", CategoryKind.EXPENSE, null, 1, null)))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.expense.isEmpty())
    }
}
