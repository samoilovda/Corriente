package com.corriente.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * A2.13: русский и английский наборы строк совпадают по ключам (`<string>` и `<plurals>`) — иначе новая строка
 * появляется только в одной локали, и в другой экран показывает fallback.
 */
class StringResourcesParityTest {

    private fun keys(path: String): Set<String> {
        val text = File(path).readText()
        return Regex("""<(?:string|plurals|string-array)\s+name="([^"]+)"""").findAll(text).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun `values and values-en define the same keys`() {
        val ru = keys("src/main/res/values/strings.xml")
        val en = keys("src/main/res/values-en/strings.xml")
        assertEquals("нет в values-en", emptySet<String>(), ru - en)
        assertEquals("нет в values", emptySet<String>(), en - ru)
    }
}
