package com.corriente.data.repository

import com.corriente.data.db.entity.CategoryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryRepositoryTest {

    private fun repo(): Pair<CategoryRepository, FakeCategoryDao> {
        val dao = FakeCategoryDao()
        return CategoryRepository(dao) to dao
    }

    // Приёмочный тест T1.4: слияние переносит операции и удаляет исходную категорию.
    @Test
    fun `merge moves transactions to the target and the source category disappears`() = runTest {
        val (repository, dao) = repo()
        val food = repository.create("Еда", CategoryKind.EXPENSE, color = 1)
        val groceries = repository.create("Продукты", CategoryKind.EXPENSE, color = 2)
        dao.attachTransaction("t1", food.id)
        dao.attachTransaction("t2", food.id)
        dao.attachTransaction("t3", groceries.id)

        repository.mergeInto(fromId = food.id, intoId = groceries.id)

        assertNull(repository.getById(food.id))
        assertEquals(setOf("t1", "t2", "t3"), dao.transactionsOf(groceries.id))
        assertEquals(listOf("Продукты"), repository.observeActive().first().map { it.name })
    }

    @Test
    fun `merge refuses two categories of different kinds`() = runTest {
        val (repository, _) = repo()
        val expense = repository.create("Транспорт", CategoryKind.EXPENSE, color = 0)
        val income = repository.create("Зарплата", CategoryKind.INCOME, color = 0)
        val error = runCatching { repository.mergeInto(expense.id, income.id) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `merge refuses a category that still has subcategories`() = runTest {
        val (repository, _) = repo()
        val parent = repository.create("Дом", CategoryKind.EXPENSE, color = 0)
        repository.create("Аренда", CategoryKind.EXPENSE, parentId = parent.id, color = 0)
        val other = repository.create("Прочее", CategoryKind.EXPENSE, color = 0)
        val error = runCatching { repository.mergeInto(parent.id, other.id) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `a subcategory must sit under a top-level category of the same kind`() = runTest {
        val (repository, _) = repo()
        val top = repository.create("Еда", CategoryKind.EXPENSE, color = 0)
        val sub = repository.create("Кафе", CategoryKind.EXPENSE, parentId = top.id, color = 0)

        // второй уровень вложенности запрещён
        val deep = runCatching {
            repository.create("Завтрак", CategoryKind.EXPENSE, parentId = sub.id, color = 0)
        }.exceptionOrNull()
        assertTrue(deep is IllegalArgumentException)

        // родитель другого типа запрещён
        val income = repository.create("Бонус", CategoryKind.INCOME, color = 0)
        val wrongKind = runCatching {
            repository.create("Премия", CategoryKind.INCOME, parentId = top.id, color = 0)
        }.exceptionOrNull()
        assertTrue(wrongKind is IllegalArgumentException)
        assertNull(repository.getById(income.id)?.parentId)
    }

    @Test
    fun `archive hides a category and unarchive brings it back`() = runTest {
        val (repository, _) = repo()
        val c = repository.create("Развлечения", CategoryKind.EXPENSE, color = 0)

        repository.archive(c.id)
        assertTrue(repository.observeActive().first().isEmpty())
        assertEquals(listOf(c.id), repository.observeArchived().first().map { it.id })

        repository.unarchive(c.id)
        assertEquals(listOf(c.id), repository.observeActive().first().map { it.id })
    }

    @Test
    fun `deleteIfUnused refuses a category with transactions`() = runTest {
        val (repository, dao) = repo()
        val c = repository.create("Еда", CategoryKind.EXPENSE, color = 0)
        dao.attachTransaction("t1", c.id)
        assertFalse(repository.deleteIfUnused(c.id))
        assertEquals(c.id, repository.getById(c.id)?.id)
    }
}
