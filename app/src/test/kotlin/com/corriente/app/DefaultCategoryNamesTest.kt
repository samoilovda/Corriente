package com.corriente.app

import com.corriente.data.seed.DEFAULT_CATEGORIES
import org.junit.Assert.assertEquals
import org.junit.Test

/** A2.13: у каждой типовой категории есть строковый ресурс с именем. */
class DefaultCategoryNamesTest {
    @Test
    fun `every seed category has a localized name resource`() {
        assertEquals(DEFAULT_CATEGORIES.map { it.id }.toSet(), defaultCategoryNameRes.keys)
    }
}
