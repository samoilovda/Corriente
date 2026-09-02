package com.corriente.data.quickentry

import com.corriente.data.model.Txn
import java.time.LocalDate

/** R2.2: вид шаблона — расход или доход (перевод не участвует, I-11). */
enum class FrequentEntryKind { EXPENSE, INCOME }

/**
 * Шаблон быстрого ввода: уникальное сочетание счёт + категория + сумма, встретившееся в
 * недавней истории. Тап по нему на экране ввода подставляет всё сразу — остаётся нажать
 * «Сохранить» (R2.2).
 */
data class FrequentEntry(
    val kind: FrequentEntryKind,
    val accountId: String,
    val categoryId: String?,
    val amountMinor: Long,
    val currencyCode: String,
)

/** Окно истории, по которому считаются «частые» шаблоны — то же, что у частых категорий виджета. */
const val FREQUENT_ENTRIES_WINDOW_DAYS = 90L

/** Комбинация должна повториться хотя бы столько раз, чтобы считаться «частой», а не разовой. */
const val FREQUENT_ENTRIES_MIN_OCCURRENCES = 2

/** Сколько шаблонов показывать строкой «частые» над клавиатурой (R2.2) — лента не должна раздуваться. */
const val FREQUENT_ENTRY_SUGGESTIONS_LIMIT = 8

/**
 * Чистая функция, по образцу [com.corriente.data.widget.buildWidgetSnapshot]: те же входы →
 * тот же результат, никаких обращений к БД/времени/локали.
 *
 * Ранжирование — по числу повторений (чаще — выше), при равенстве — по дате последнего
 * использования (свежее — выше). Переводы не участвуют (I-11: у них нет категории, и «сумма
 * перевода» неоднозначна — их две, в разных валютах).
 *
 * @param transactions вся история (или хотя бы окно [windowDays] назад от [today] — более старые
 * операции функция всё равно игнорирует).
 * @param today «сегодня» — начало окна отсчитывается от этой даты, не от `LocalDate.now()`.
 * @param limit сколько шаблонов вернуть максимум (строка «частые» коротка).
 */
fun frequentEntries(
    transactions: List<Txn>,
    today: LocalDate,
    limit: Int,
    windowDays: Long = FREQUENT_ENTRIES_WINDOW_DAYS,
    minOccurrences: Int = FREQUENT_ENTRIES_MIN_OCCURRENCES,
): List<FrequentEntry> {
    if (limit <= 0) return emptyList()
    val since = today.minusDays(windowDays)

    fun entryOf(txn: Txn): FrequentEntry? = when (txn) {
        is Txn.Expense -> FrequentEntry(
            FrequentEntryKind.EXPENSE, txn.accountId, txn.categoryId, txn.amount.amount.raw, txn.amount.currency.code,
        )
        is Txn.Income -> FrequentEntry(
            FrequentEntryKind.INCOME, txn.accountId, txn.categoryId, txn.amount.amount.raw, txn.amount.currency.code,
        )
        is Txn.Transfer -> null // I-11: перевод не входит в шаблоны быстрого ввода
    }

    val counts = LinkedHashMap<FrequentEntry, Int>()
    val lastUsed = HashMap<FrequentEntry, LocalDate>()
    transactions.asSequence()
        .filter { it.date in since..today }
        .forEach { txn ->
            val entry = entryOf(txn) ?: return@forEach
            counts[entry] = (counts[entry] ?: 0) + 1
            val previous = lastUsed[entry]
            if (previous == null || txn.date > previous) lastUsed[entry] = txn.date
        }

    return counts.entries
        .filter { it.value >= minOccurrences }
        .sortedWith(
            compareByDescending<Map.Entry<FrequentEntry, Int>> { it.value }
                .thenByDescending { lastUsed.getValue(it.key) },
        )
        .take(limit)
        .map { it.key }
}
