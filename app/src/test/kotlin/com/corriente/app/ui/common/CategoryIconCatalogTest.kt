package com.corriente.app.ui.common

import com.corriente.data.category.CATEGORY_ICON_RULES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryIconCatalogTest {

    // Автоподбор живёт в :core:data и знает только ключи — каждый должен существовать в каталоге приложения,
    // иначе на экране вместо иконки была бы буква.
    @Test
    fun everySuggestedKeyExistsInTheCatalog() {
        val missing = CATEGORY_ICON_RULES.map { it.second }.filterNot { it in CategoryIconCatalog.keys }
        assertTrue("нет в каталоге: $missing", missing.isEmpty())
    }

    @Test
    fun sectionsCoverEveryKeyExactlyOnce() {
        val inSections = CategoryIconCatalog.sections.flatMap { it.keys }
        assertEquals(inSections.size, inSections.toSet().size)
        assertEquals(CategoryIconCatalog.keys, inSections.toSet())
    }
}
