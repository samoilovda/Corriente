package com.corriente.data.category

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryIconSuggestTest {

    private fun key(name: String) = materialIconKey(suggestCategoryIcon(name))

    @Test
    fun typicalRussianAndEnglishNames() {
        mapOf(
            "Продукты" to "local_grocery_store", "Groceries" to "local_grocery_store",
            "Такси" to "local_taxi", "Транспорт" to "directions_bus", "Автомобиль" to "directions_car",
            "Кафе и рестораны" to "local_cafe", "Cafes & restaurants" to "local_cafe",
            "Жильё" to "home", "Коммунальные услуги" to "lightbulb", "Связь и интернет" to "wifi",
            "Здоровье" to "local_hospital", "Одежда и обувь" to "checkroom", "Clothing & shoes" to "checkroom",
            "Зарплата" to "payments", "Проценты по вкладу" to "savings", "Taxes & fees" to "receipt_long",
            "Прочее" to "more_horiz",
        ).forEach { (name, expected) -> assertEquals(name, expected, key(name)) }
    }

    @Test
    fun wordsNotSubstrings() {
        assertEquals("spa", key("Beauty & care")) // не «car»
        assertEquals("content_cut", key("Барбершоп"))
        assertEquals("content_cut", key("Barber")) // не «bar»
        assertEquals("pets", key("Домашние животные")) // не «дом»
        assertNull(key("Xyz"))
        assertNull(key("  "))
    }

    @Test
    fun refsRoundTrip() {
        assertEquals("mi:home", materialIconRef("home"))
        assertEquals("home", materialIconKey("mi:home"))
        assertNull(materialIconKey("🏠"))
        assertNull(materialIconKey(null))
    }

    @Test
    fun overviewSettingsDecode() {
        assertEquals(listOf("a", "b"), OverviewCategoriesSettings.decode(" a, ,b "))
        assertEquals(emptyList<String>(), OverviewCategoriesSettings.decode(null))
    }
}
