package com.corriente.data.quickentry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavouriteCategoriesTest {

    private val expenses = setOf("e1", "e2", "e3", "e4", "e5")
    private val incomes = setOf("i1", "i2")

    @Test
    fun `disabled by default and can be switched`() {
        val favourites = InMemoryFavouriteCategories()
        assertFalse(favourites.state.value.enabled)
        favourites.setEnabled(true)
        assertTrue(favourites.state.value.enabled)
    }

    @Test
    fun `four favourites per kind, the fifth is refused, income has its own four`() {
        val favourites = InMemoryFavouriteCategories()
        listOf("e1", "e2", "e3", "e4").forEach { assertTrue(favourites.add(it, expenses)) }

        assertFalse(favourites.add("e5", expenses))
        assertTrue(favourites.add("i1", incomes))
        assertEquals(listOf("e1", "e2", "e3", "e4", "i1"), favourites.state.value.ids)
    }

    @Test
    fun `adding twice changes nothing and removing frees a slot`() {
        val favourites = InMemoryFavouriteCategories()
        listOf("e1", "e2", "e3", "e4").forEach { favourites.add(it, expenses) }
        assertTrue(favourites.add("e1", expenses))
        assertEquals(4, favourites.state.value.ids.size)

        favourites.remove("e2")
        assertTrue(favourites.add("e5", expenses))
        assertEquals(listOf("e1", "e3", "e4", "e5"), favourites.state.value.ids)
    }

    @Test
    fun `favouritesOfKind keeps order and drops categories that are gone`() {
        val ids = listOf("e3", "i1", "gone", "e1")
        assertEquals(listOf("e3", "e1"), favouritesOfKind(ids, expenses))
    }

    @Test
    fun `codec survives garbage`() {
        assertEquals(listOf("a", "b"), FavouriteIdsCodec.decode("a,,b,a"))
        assertEquals(emptyList<String>(), FavouriteIdsCodec.decode(null))
        assertEquals("a,b", FavouriteIdsCodec.encode(listOf("a", "", "b", "x,y")))
    }
}
