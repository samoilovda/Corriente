package com.corriente.data.export

import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import java.io.OutputStream

/**
 * R3.3: экспорт отчёта и списка операций в CSV через SAF (`kotlin-csv-jvm`, уже в зависимостях
 * `:core:data` — BUILD_PLAN.md §1.3, без новых зависимостей). Суммы приходят сюда уже строкой,
 * отформатированной вызывающим через [com.corriente.money.MoneyFormatter] — тем же фиксированным,
 * не зависящим от локали устройства форматом, что и на экране (I-25): здесь нет ни одного
 * числового поля, которое библиотека или JVM могли бы отформатировать по-своему. Единственная
 * работа этого файла — собрать строки таблицы и корректно экранировать запятые/кавычки в
 * произвольном пользовательском тексте (название категории, заметка), чем и занимается
 * `kotlin-csv-jvm` по построению (RFC4180-подобное экранирование).
 */

/** Одна строка выгрузки отчёта по категориям. */
data class ReportCsvRow(
    val category: String,
    val amountText: String,
    val sharePercent: Int,
    /** Текст сравнения с прошлым периодом (R3.1), уже готовый — "+18 % vs. last month" / "—". */
    val changeText: String,
    /** A3.15: сумма числом («1234.56», без символа и группировки) — для сложения в таблице. */
    val amountValue: String = "",
    /** A3.15: код валюты суммы. */
    val currency: String = "",
)

/** Одна строка выгрузки списка операций (с уже применёнными на экране фильтрами). */
data class TxnCsvRow(
    val date: String,
    val account: String,
    val category: String,
    val note: String,
    val amountText: String,
    /** A3.15: сумма числом со знаком («-1234.56»); у перевода — сторона-источник. */
    val amountValue: String = "",
    /** A3.15: код валюты [amountValue]. */
    val currency: String = "",
)

/**
 * Заголовки колонок CSV (A2.13): текст — данные экспорта, но язык должен быть языком пользователя, поэтому строит
 * их `:app` из ресурсов; [ENGLISH] — нейтральный запасной вариант (тесты, превью).
 */
data class CsvHeaders(val report: List<String>, val transactions: List<String>) {
    companion object {
        val ENGLISH = CsvHeaders(
            report = listOf("Category", "Amount", "Amount (number)", "Currency", "Share, %", "Change vs. previous period"),
            transactions = listOf("Date", "Account", "Category", "Note", "Amount", "Amount (number)", "Currency"),
        )
    }
}

object CsvExport {

    fun reportCsv(rows: List<ReportCsvRow>, headers: CsvHeaders = CsvHeaders.ENGLISH): String = csvWriter().writeAllAsString(
        listOf(headers.report) + rows.map { r -> listOf(r.category, r.amountText, r.amountValue, r.currency, r.sharePercent.toString(), r.changeText) },
    )

    fun txnCsv(rows: List<TxnCsvRow>, headers: CsvHeaders = CsvHeaders.ENGLISH): String = csvWriter().writeAllAsString(
        listOf(headers.transactions) + rows.map { r -> listOf(r.date, r.account, r.category, r.note, r.amountText, r.amountValue, r.currency) },
    )

    /**
     * A3.15: байты файла — UTF-8 **с BOM**: без него Excel на Windows открывает кириллицу «кракозябрами». BOM живёт
     * только на уровне файла — [reportCsv]/[txnCsv] возвращают чистую строку.
     */
    fun encodeForFile(csv: String): ByteArray = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + csv.toByteArray(Charsets.UTF_8)

    /** UTF-8 (с BOM, см. [encodeForFile]) — читается и `MonefyCsvParser`-ом [com.corriente.data.imports.MonefyCsvParser] на импорте. */
    fun writeReportCsv(rows: List<ReportCsvRow>, out: OutputStream, headers: CsvHeaders = CsvHeaders.ENGLISH) {
        out.write(encodeForFile(reportCsv(rows, headers)))
    }

    fun writeTxnCsv(rows: List<TxnCsvRow>, out: OutputStream, headers: CsvHeaders = CsvHeaders.ENGLISH) {
        out.write(encodeForFile(txnCsv(rows, headers)))
    }
}
