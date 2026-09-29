package com.corriente.data.export

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R3.3: генерация CSV — числа уже строки [MoneyFormatter]-формата (I-25), здесь проверяем
 * только сборку строк и то, что запятые/кавычки в пользовательском тексте не ломают структуру
 * файла — раунд-трип через тот же `kotlin-csv-jvm` (`csvReader`, как в `MonefyCsvParser`).
 */
class CsvExportTest {

    private fun parse(csv: String): List<List<String>> = csvReader().readAll(csv)

    @Test
    fun `report csv starts with the header row`() {
        val csv = CsvExport.reportCsv(emptyList())
        val rows = parse(csv)
        assertEquals(listOf(CsvHeaders.ENGLISH.report), rows)
    }

    @Test
    fun `report csv rows round-trip through parsing unchanged`() {
        val rows = listOf(
            ReportCsvRow("Еда", "32 400.00 ₽", 41, "+18 % к прошлому месяцу", "32400.00", "RUB"),
            ReportCsvRow("Транспорт", "1 000.00 ₽", 1, "—", "1000.00", "RUB"),
        )
        val parsed = parse(CsvExport.reportCsv(rows)).drop(1) // без заголовка
        assertEquals(
            listOf(
                listOf("Еда", "32 400.00 ₽", "32400.00", "RUB", "41", "+18 % к прошлому месяцу"),
                listOf("Транспорт", "1 000.00 ₽", "1000.00", "RUB", "1", "—"),
            ),
            parsed,
        )
    }

    // Критерий R3.3: запятая в названии категории не рвёт структуру файла.
    @Test
    fun `a comma inside a category name is escaped and round-trips correctly`() {
        val rows = listOf(ReportCsvRow("Еда, кафе", "500.00 ₽", 100, "—"))
        val parsed = parse(CsvExport.reportCsv(rows)).drop(1)
        assertEquals(listOf(listOf("Еда, кафе", "500.00 ₽", "", "", "100", "—")), parsed)
    }

    // Критерий R3.3: кавычка в названии категории тоже не рвёт структуру файла.
    @Test
    fun `a quote inside a category name is escaped and round-trips correctly`() {
        val rows = listOf(ReportCsvRow("""Кафе "Уют"""", "500.00 ₽", 100, "—"))
        val parsed = parse(CsvExport.reportCsv(rows)).drop(1)
        assertEquals(listOf(listOf("""Кафе "Уют"""", "500.00 ₽", "", "", "100", "—")), parsed)
    }

    // Запятая и кавычка одновременно, плюс перевод строки в заметке операции.
    @Test
    fun `commas, quotes and newlines in a transaction note all round-trip correctly`() {
        val rows = listOf(
            TxnCsvRow("2026-03-05", "Наличные", "Еда, кафе", """заметка "с кавычками", и запятой""", "-500.00 ₽"),
            TxnCsvRow("2026-03-06", "Карта", "Такси", "заметка\nс переводом строки", "-300.00 ₽"),
        )
        val parsed = parse(CsvExport.txnCsv(rows)).drop(1)
        assertEquals(
            listOf(
                listOf("2026-03-05", "Наличные", "Еда, кафе", """заметка "с кавычками", и запятой""", "-500.00 ₽", "", ""),
                listOf("2026-03-06", "Карта", "Такси", "заметка\nс переводом строки", "-300.00 ₽", "", ""),
            ),
            parsed,
        )
    }

    @Test
    fun `txn csv starts with the header row`() {
        val rows = parse(CsvExport.txnCsv(emptyList()))
        assertEquals(listOf(CsvHeaders.ENGLISH.transactions), rows)
    }

    // Пустая заметка/пустое сравнение — валидная ячейка, не наличие/отсутствие столбца.
    @Test
    fun `empty note is an empty cell, not a missing column`() {
        val rows = listOf(TxnCsvRow("2026-03-05", "Наличные", "Еда", "", "-500.00 ₽"))
        val parsed = parse(CsvExport.txnCsv(rows)).drop(1)
        assertEquals(7, parsed.single().size)
        assertEquals("", parsed.single()[3])
    }

    // A3.15: файл — UTF-8 с BOM (Excel), строка без BOM; числовая колонка и валюта — отдельно от форматированной суммы.
    @Test
    fun `file bytes start with a UTF-8 BOM while the csv string does not`() {
        val csv = CsvExport.txnCsv(listOf(TxnCsvRow("2026-03-05", "Нал", "Еда", "", "-1 234.56 ₽", "-1234.56", "RUB")))
        assertEquals(false, csv.startsWith("\uFEFF"))
        val bytes = CsvExport.encodeForFile(csv)
        assertEquals(listOf(0xEF, 0xBB, 0xBF), bytes.take(3).map { it.toInt() and 0xFF })
        assertEquals(csv, String(bytes, 3, bytes.size - 3, Charsets.UTF_8))
        assertEquals(listOf("2026-03-05", "Нал", "Еда", "", "-1 234.56 ₽", "-1234.56", "RUB"), parse(csv).drop(1).single())
    }
}
