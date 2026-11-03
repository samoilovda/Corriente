package com.corriente.data.desktop

import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * W13: категории с одинаковой позицией (созданы на двух устройствах между обменами) идут в устойчивом порядке — по
 * имени, затем по id, — а не как попало у SQLite.
 */
class CategoryOrderTest {

    @Test
    fun equalDisplayOrderFallsBackToNameThenId() = runBlocking {
        val db = DesktopDatabase.inMemory()
        val dao = db.categoryDao()
        dao.insert(CategoryEntity("z-2", "Ягоды", CategoryKind.EXPENSE, null, 0, displayOrder = 500))
        dao.insert(CategoryEntity("b-1", "Арбузы", CategoryKind.EXPENSE, null, 0, displayOrder = 500))
        dao.insert(CategoryEntity("a-3", "Вишня", CategoryKind.EXPENSE, null, 0, displayOrder = 499))

        val ids = dao.observeActive().first().filter { it.displayOrder >= 499 }.map { it.id }
        assertEquals(listOf("a-3", "b-1", "z-2"), ids)
        db.close()
    }
}
