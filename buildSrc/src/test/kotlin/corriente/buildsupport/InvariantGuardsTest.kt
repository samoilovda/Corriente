package corriente.buildsupport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Тест механизма из BUILD_PLAN.md §0 (правило 5, T0.5): скан обязан ловить нарушение
 * и не давать ложных срабатываний. Плюс проход по реальному репозиторию — как гейт.
 */
class InvariantGuardsTest {

    @Test
    fun `flags Double and Float in the money modules including casts`() {
        val src = """
            package com.corriente.data.x
            fun bad(v: Long): Float = v.toFloat()
            val rate: Double = 1.0
        """.trimIndent()
        val hits = InvariantGuards.scanKotlin("core/data/src/main/kotlin/X.kt", src)
        assertEquals(3, hits.size) // Double, Float, .toFloat(
    }

    @Test
    fun `flags locale-dependent money formatting anywhere`() {
        val src = """
            import java.text.DecimalFormat
            val s = DecimalFormat("#0.00").format(x)
            val n = NumberFormat.getInstance().parse(t)
        """.trimIndent()
        val hits = InvariantGuards.scanKotlin("app/src/main/kotlin/Y.kt", src)
        assertEquals(2, hits.size)
    }

    @Test
    fun `flags fallbackToDestructiveMigration`() {
        val hits = InvariantGuards.scanKotlin(
            "core/data/src/main/kotlin/Db.kt",
            "builder.fallbackToDestructiveMigration()",
        )
        assertEquals(1, hits.size)
    }

    @Test
    fun `ignores forbidden tokens that live only in comments`() {
        val src = """
            // этот код не использует Double
            /* и DecimalFormat здесь только в тексте */
            fun clean(v: Long): Long = v
        """.trimIndent()
        assertEquals(emptyList<String>(), InvariantGuards.scanKotlin("core/money/src/main/kotlin/Z.kt", src))
    }

    @Test
    fun `allows Float in the whitelisted Canvas file but not elsewhere in app`() {
        val src = "val h: Float = size.height"
        assertEquals(emptyList<String>(), InvariantGuards.scanKotlin(InvariantGuards.floatAllowlist.first(), src))
        assertTrue(InvariantGuards.scanKotlin("app/src/main/kotlin/com/corriente/app/ui/other/Other.kt", src).isNotEmpty())
    }

    @Test
    fun `allows a plain unpaired transfer half - no false positive on the word float`() {
        // "floating" содержит "float", но не как отдельный токен — \b не должен срабатывать
        assertEquals(
            emptyList<String>(),
            InvariantGuards.scanKotlin("core/money/src/main/kotlin/A.kt", "val floatingPoint = 0L // no"),
        )
    }

    // F1.1 — арифметика денег в обход Money.
    @Test
    fun `flags Long function references used for money arithmetic`() {
        val bad = """
            val nets = mutableMapOf<String, Long>()
            nets.merge(code, -txn.amount.amount.raw, Long::plus)
        """.trimIndent()
        assertEquals(1, InvariantGuards.scanKotlin("app/src/main/kotlin/com/corriente/app/ui/T.kt", bad).size)
    }

    @Test
    fun `does not flag Long plus reference in a file with no money content`() {
        val ok = "val n = counts.fold(0L, Long::plus)"
        assertEquals(emptyList<String>(), InvariantGuards.scanKotlin("app/src/main/kotlin/com/corriente/app/ui/Counter.kt", ok))
    }

    @Test
    fun `does not flag Money-based accumulation`() {
        val ok = """
            val nets = mutableMapOf<CurrencyCode, Money>()
            nets.merge(txn.amount.currency, -txn.amount, Money::plus)
            val x = Minor(0)
        """.trimIndent()
        assertEquals(emptyList<String>(), InvariantGuards.scanKotlin("app/src/main/kotlin/com/corriente/app/ui/T2.kt", ok))
    }

    @Test
    fun `flags uses-permission in a manifest`() {
        val xml = """
            <manifest>
                <!-- uses-permission в комментарии не считается -->
                <uses-permission android:name="android.permission.INTERNET" />
            </manifest>
        """.trimIndent()
        assertEquals(1, InvariantGuards.scanManifest("app/src/main/AndroidManifest.xml", xml).size)
    }

    @Test
    fun `the real repository is clean`() {
        val repoRoot = generateSequence(File(".").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").exists() && File(it, "buildSrc").exists() }
            ?: error("не найден корень репозитория")

        val violations = mutableListOf<String>()
        repoRoot.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != "buildSrc" }
            .filter { it.isFile }
            .forEach { file ->
                val rel = file.relativeTo(repoRoot).path.replace(File.separatorChar, '/')
                when {
                    rel.endsWith(".kt") && ("/src/main/" in rel || "/src/test/" in rel) ->
                        violations += InvariantGuards.scanKotlin(rel, file.readText())
                    file.name == "AndroidManifest.xml" ->
                        violations += InvariantGuards.scanManifest(rel, file.readText())
                }
            }
        assertEquals(violations.joinToString("\n\n"), emptyList<String>(), violations)
    }
}
