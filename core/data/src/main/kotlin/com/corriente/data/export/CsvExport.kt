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
)

/** Одна строка выгрузки списка операций (с уже применёнными на экране фильтрами). */
data class TxnCsvRow(
    val date: String,
    val account: String,
    val category: String,
    val note: String,
    val amountText: String,
)

/**
 * Заголовки колонок CSV (A2.13): текст — данные экспорта, но язык должен быть языком пользователя, поэтому строит
 * их `:app` из ресурсов; [ENGLISH] — нейтральный запасной вариант (тесты, превью).
 */
data class CsvHeaders(val report: List<String>, val transactions: List<String>) {
    companion object {
        val ENGLISH = CsvHeaders(
            report = listOf("Category", "Amount", "Share, %", "Change vs. previous period"),
            transactions = listOf("Date", "Account", "Category", "Note", "Amount"),
        )
    }
}

object CsvExport {

    fun reportCsv(rows: List<ReportCsvRow>, headers: CsvHeaders = CsvHeaders.ENGLISH): String = csvWriter().writeAllAsString(
        listOf(headers.report) + rows.map { r -> listOf(r.category, r.amountText, r.sharePercent.toString(), r.changeText) },
    )

    fun txnCsv(rows: List<TxnCsvRow>, headers: CsvHeaders = CsvHeaders.ENGLISH): String = csvWriter().writeAllAsString(
        listOf(headers.transactions) + rows.map { r -> listOf(r.date, r.account, r.category, r.note, r.amountText) },
    )

    /** UTF-8 — то же самое, что читает [com.corriente.data.imports.MonefyCsvParser] на импорте. */
    fun writeReportCsv(rows: List<ReportCsvRow>, out: OutputStream, headers: CsvHeaders = CsvHeaders.ENGLISH) {
        out.write(reportCsv(rows, headers).toByteArray(Charsets.UTF_8))
    }

    fun writeTxnCsv(rows: List<TxnCsvRow>, out: OutputStream, headers: CsvHeaders = CsvHeaders.ENGLISH) {
        out.write(txnCsv(rows, headers).toByteArray(Charsets.UTF_8))
    }
}
