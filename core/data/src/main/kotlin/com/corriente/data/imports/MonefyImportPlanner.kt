package com.corriente.data.imports

import com.corriente.money.CurrencyCode
import java.time.LocalDate
import kotlin.math.abs

/** Категория для непарной половинки перевода (MONEFY_IMPORT.md §3). */
const val UNPAIRED_TRANSFER_CATEGORY = "[перевод без пары]"

enum class MonefyTxnKind { EXPENSE, INCOME }

enum class ReviewReason {
    /** Лишние ненулевые дробные знаки округлены (вариант А). */
    EXCESS_PRECISION,

    /** Два одинаковых перевода в один день между теми же счетами — пары неоднозначны. */
    AMBIGUOUS_PAIRING,

    /** Валюта счёта в экспорте не соответствует исторической сумме (неявный курс 1.0). */
    ANOMALOUS_CURRENCY,

    /** Один счёт встречается в файле с разными валютами. */
    ACCOUNT_CURRENCY_CONFLICT,
}

data class PlannedAccount(
    val name: String,
    val currency: CurrencyCode,
    val openingBalanceMinor: Long,
)

data class PlannedTxn(
    val line: Int,
    val date: LocalDate,
    val account: String,
    /** null не бывает — либо реальная категория, либо [UNPAIRED_TRANSFER_CATEGORY]. */
    val category: String,
    /** Всегда положительна (I-1) — знак в [kind]. */
    val amountMinor: Long,
    val currency: CurrencyCode,
    val kind: MonefyTxnKind,
    /** Натуральный ключ строки CSV (без индекса повторения) — для идемпотентности импорта (I-19). */
    val naturalKey: String,
    val unpairedHalf: Boolean = false,
)

data class PlannedTransfer(
    val fromLine: Int,
    val toLine: Int,
    val date: LocalDate,
    val fromAccount: String,
    val fromAmountMinor: Long,
    val fromCurrency: CurrencyCode,
    val toAccount: String,
    val toAmountMinor: Long,
    val toCurrency: CurrencyCode,
    val naturalKey: String,
    val review: ReviewReason? = null,
)

data class ReviewItem(val reason: ReviewReason, val lines: List<Int>, val message: String)

data class MonefyImportPlan(
    val accounts: List<PlannedAccount>,
    val categories: List<String>,
    val plainTxns: List<PlannedTxn>,
    val transfers: List<PlannedTransfer>,
    val reviews: List<ReviewItem>,
    val errors: List<MonefyRowError>,
)

/**
 * T3.2: классификация разобранных строк и склейка переводов (MONEFY_IMPORT.md §3, §5 п.3–5).
 * Ничего не «угадывает»: неоднозначные пары, аномальные валюты и округлённые суммы попадают
 * в [MonefyImportPlan.reviews], непарные половинки — в [MonefyImportPlan.plainTxns] с категорией
 * [UNPAIRED_TRANSFER_CATEGORY].
 */
object MonefyImportPlanner {

    private val INITIAL = Regex("""Initial balance '(.+)'""")
    private val TO = Regex("""To '(.+)'""")
    private val FROM = Regex("""From '(.+)'""")

    private fun naturalKey(row: MonefyRow) =
        "${row.date}|${row.account}|${row.rawCategory}|${row.amountText}|${row.currency.code}"

    private data class Half(
        val row: MonefyRow,
        val thisAccount: String,
        val otherAccount: String,
        val isTo: Boolean,
    )

    fun plan(csv: MonefyCsvResult): MonefyImportPlan {
        val rows = csv.rows
        val reviews = mutableListOf<ReviewItem>()

        // --- счета и валюты ---
        val accountCurrencies = LinkedHashMap<String, CurrencyCode>()
        rows.forEach { row ->
            val existing = accountCurrencies[row.account]
            if (existing == null) {
                accountCurrencies[row.account] = row.currency
            } else if (existing != row.currency) {
                reviews += ReviewItem(
                    ReviewReason.ACCOUNT_CURRENCY_CONFLICT, listOf(row.line),
                    "счёт «${row.account}» встречается с разными валютами: $existing и ${row.currency}",
                )
            }
        }
        // счета, встречающиеся только в 'To'/'From' как получатель/источник, но без своих строк
        rows.forEach { row ->
            TO.matchEntire(row.rawCategory)?.groupValues?.get(1)?.let { accountCurrencies.putIfAbsent(it, row.currency) }
            FROM.matchEntire(row.rawCategory)?.groupValues?.get(1)?.let { accountCurrencies.putIfAbsent(it, row.currency) }
        }

        val openingByAccount = HashMap<String, Long>()
        val halves = mutableListOf<Half>()
        val plainTxns = mutableListOf<PlannedTxn>()
        val categories = LinkedHashSet<String>()

        rows.forEach { row ->
            val initial = INITIAL.matchEntire(row.rawCategory)
            val to = TO.matchEntire(row.rawCategory)
            val from = FROM.matchEntire(row.rawCategory)
            when {
                initial != null -> openingByAccount[row.account] = row.amount.raw
                to != null -> halves += Half(row, row.account, to.groupValues[1], isTo = true)
                from != null -> halves += Half(row, row.account, from.groupValues[1], isTo = false)
                else -> {
                    categories += row.rawCategory
                    plainTxns += PlannedTxn(
                        line = row.line, date = row.date, account = row.account, category = row.rawCategory,
                        amountMinor = abs(row.amount.raw), currency = row.currency,
                        kind = if (row.amount.raw < 0) MonefyTxnKind.EXPENSE else MonefyTxnKind.INCOME,
                        naturalKey = naturalKey(row),
                    )
                }
            }
        }

        // --- склейка пар ---
        val toHalves = halves.filter { it.isTo }
        val fromHalves = halves.filter { !it.isTo }.toMutableList()
        val transfers = mutableListOf<PlannedTransfer>()
        val pairedToLines = HashSet<Int>()

        data class PairKey(val date: LocalDate, val from: String, val to: String, val converted: Long)
        fun keyOf(h: Half) = PairKey(h.row.date, h.thisAccount, h.otherAccount, h.row.convertedAbs)
        // группа из ≥2 одинаковых 'To' в один день между теми же счетами → все её пары неоднозначны
        val ambiguousKeys = toHalves.groupBy { keyOf(it) }.filterValues { it.size > 1 }.keys

        toHalves.forEach { toH ->
            val candidates = fromHalves.filter {
                it.thisAccount == toH.otherAccount &&
                    it.otherAccount == toH.thisAccount &&
                    it.row.date == toH.row.date &&
                    it.row.convertedAbs == toH.row.convertedAbs
            }
            if (candidates.isEmpty()) return@forEach
            val fromH = candidates.first()
            fromHalves.remove(fromH)
            pairedToLines += toH.row.line

            val excess = toH.row.amountRoundedFromExcess || fromH.row.amountRoundedFromExcess
            val fromMinor = abs(toH.row.amount.raw)
            val toMinor = abs(fromH.row.amount.raw)
            val crossCurrency = toH.row.currency != fromH.row.currency
            // неявный курс 1.0 при разных валютах — валюта счёта не совпадает с исторической суммой
            val anomalous = crossCurrency && fromMinor == fromH.row.convertedAbs && toMinor == fromH.row.convertedAbs

            val review = when {
                keyOf(toH) in ambiguousKeys -> ReviewReason.AMBIGUOUS_PAIRING
                anomalous -> ReviewReason.ANOMALOUS_CURRENCY
                excess -> ReviewReason.EXCESS_PRECISION
                else -> null
            }
            transfers += PlannedTransfer(
                fromLine = toH.row.line, toLine = fromH.row.line, date = toH.row.date,
                fromAccount = toH.thisAccount, fromAmountMinor = fromMinor, fromCurrency = toH.row.currency,
                toAccount = fromH.thisAccount, toAmountMinor = toMinor, toCurrency = fromH.row.currency,
                naturalKey = naturalKey(toH.row) + "->" + naturalKey(fromH.row),
                review = review,
            )
        }

        // непарные половинки → обычные операции с [перевод без пары]
        halves.filter { it.isTo && it.row.line !in pairedToLines }.forEach { h ->
            plainTxns += unpairedHalf(h, MonefyTxnKind.EXPENSE)
        }
        fromHalves.forEach { h -> plainTxns += unpairedHalf(h, MonefyTxnKind.INCOME) }

        // --- reviews ---
        transfers.filter { it.review == ReviewReason.AMBIGUOUS_PAIRING }
            .groupBy { Triple(it.date, it.fromAccount, it.toAccount) }
            .forEach { (_, group) ->
                reviews += ReviewItem(
                    ReviewReason.AMBIGUOUS_PAIRING,
                    group.flatMap { listOf(it.fromLine, it.toLine) }.sorted(),
                    "${group.size} одинаковых перевода ${group.first().fromAccount} → ${group.first().toAccount} " +
                        "за ${group.first().date} — пары построены по порядку строк, подтвердите",
                )
            }
        transfers.filter { it.review == ReviewReason.ANOMALOUS_CURRENCY }.forEach {
            reviews += ReviewItem(
                ReviewReason.ANOMALOUS_CURRENCY, listOf(it.fromLine, it.toLine),
                "перевод ${it.fromAccount} → ${it.toAccount} за ${it.date}: неявный курс 1.0 при разных " +
                    "валютах — выберите валюту суммы-приёмника",
            )
        }
        transfers.filter { it.review == ReviewReason.EXCESS_PRECISION }.forEach {
            reviews += ReviewItem(
                ReviewReason.EXCESS_PRECISION, listOf(it.fromLine, it.toLine),
                "перевод ${it.fromAccount} → ${it.toAccount} за ${it.date}: сумма округлена до точности " +
                    "валюты — подтвердите или введите точную",
            )
        }

        val accounts = accountCurrencies.map { (name, currency) ->
            PlannedAccount(name, currency, openingByAccount[name] ?: 0L)
        }

        return MonefyImportPlan(
            accounts = accounts,
            categories = categories.toList(),
            plainTxns = plainTxns,
            transfers = transfers,
            reviews = reviews,
            errors = csv.errors,
        )
    }

    private fun unpairedHalf(h: Half, kind: MonefyTxnKind) = PlannedTxn(
        line = h.row.line, date = h.row.date, account = h.thisAccount, category = UNPAIRED_TRANSFER_CATEGORY,
        amountMinor = abs(h.row.amount.raw), currency = h.row.currency, kind = kind,
        naturalKey = naturalKey(h.row), unpairedHalf = true,
    )
}
