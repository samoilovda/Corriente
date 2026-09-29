package com.corriente.app.ui.report

import android.content.Context
import com.corriente.app.R
import com.corriente.data.export.CsvHeaders

/**
 * A2.13: тексты, которые `ReportViewModel` кладёт в `ReportUiState` и в CSV, — ViewModel не знает локаль,
 * строки приходят из ресурсов ([ResourceReportTexts]); [EnglishReportTexts] — запасной вариант для тестов.
 */
interface ReportTexts {
    /** «к прошлому месяцу/кварталу/году»; `null` — сравнение неприменимо (произвольный диапазон). */
    fun changeSuffix(mode: PeriodMode): String?

    /** Подпись строки «Без категории» в CSV. */
    val noCategory: String

    val csvHeaders: CsvHeaders
}

object EnglishReportTexts : ReportTexts {
    override fun changeSuffix(mode: PeriodMode): String? = when (mode) {
        PeriodMode.MONTH -> "vs. last month"
        PeriodMode.QUARTER -> "vs. last quarter"
        PeriodMode.YEAR -> "vs. last year"
        PeriodMode.CUSTOM -> null
    }

    override val noCategory: String = "No category"
    override val csvHeaders: CsvHeaders = CsvHeaders.ENGLISH
}

/** Строки из ресурсов приложения — язык текущей локали устройства на момент вызова. */
class ResourceReportTexts(context: Context) : ReportTexts {
    private val app = context.applicationContext

    override fun changeSuffix(mode: PeriodMode): String? = when (mode) {
        PeriodMode.MONTH -> app.getString(R.string.report_change_since_month)
        PeriodMode.QUARTER -> app.getString(R.string.report_change_since_quarter)
        PeriodMode.YEAR -> app.getString(R.string.report_change_since_year)
        PeriodMode.CUSTOM -> null
    }

    override val noCategory: String get() = app.getString(R.string.txn_entry_no_category)

    override val csvHeaders: CsvHeaders
        get() = CsvHeaders(
            report = listOf(
                app.getString(R.string.csv_header_category), app.getString(R.string.csv_header_amount),
                app.getString(R.string.csv_header_amount_number), app.getString(R.string.csv_header_currency),
                app.getString(R.string.csv_header_share), app.getString(R.string.csv_header_change),
            ),
            transactions = listOf(
                app.getString(R.string.csv_header_date), app.getString(R.string.csv_header_account),
                app.getString(R.string.csv_header_category), app.getString(R.string.csv_header_note),
                app.getString(R.string.csv_header_amount), app.getString(R.string.csv_header_amount_number),
                app.getString(R.string.csv_header_currency),
            ),
        )
}
