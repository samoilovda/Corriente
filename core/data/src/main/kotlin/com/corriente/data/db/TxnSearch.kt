package com.corriente.data.db

/**
 * R2.1: превращает пользовательский текст поиска в MATCH-запрос FTS4. Каждое слово — отдельный
 * токен с префиксным поиском (`слово*`), несколько слов ANDятся неявно (синтаксис FTS4 по
 * умолчанию) — так «кофе трав» находит «кофе с травами», а не только точную фразу.
 *
 * Слова приводятся к нижнему регистру (A1.10). Кавычки внутри слова экранируются удвоением (синтаксис FTS: `"..."` — фраза), чтобы текст
 * пользователя не мог сломать синтаксис MATCH-запроса.
 *
 * @return пустая строка, если после очистки нечего искать — вызывающий код обязан не использовать
 * пустой MATCH (в [com.corriente.data.db.dao.TxnDao.search] это условие уже учтено).
 */
fun buildFtsPrefixQuery(rawQuery: String): String {
    val words = rawQuery.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return ""
    return words.joinToString(" ") { word ->
        // A1.10: `unicode61` сворачивает регистр при индексации, но не в квотированном префиксном
        // токене запроса (`"МОЛОК"*` ничего не находит) — свёртку делаем сами.
        val escaped = word.lowercase().replace("\"", "\"\"")
        // Звезда — внутри кавычек: `"слов*"`. Форма `"слов"*` префиксом не является и находила только точное слово.
        "\"$escaped*\""
    }
}

/**
 * W11: подходит ли название счёта/категории под поисковый запрос — подстрока без учёта регистра. Сравнение в Kotlin, а
 * не `LOWER(name) LIKE` в SQL: встроенные в SQLite `LOWER()` и `LIKE` сворачивают регистр только у ASCII, и на десктопе
 * (SQLite без ICU) «тинькофф» не находил «Тинькофф». Справочники маленькие — фильтр в памяти ничего не стоит.
 */
fun nameMatchesQuery(name: String, rawQuery: String): Boolean {
    val needle = rawQuery.trim().lowercase()
    return needle.isNotEmpty() && name.lowercase().contains(needle)
}
