package com.corriente.data.quickentry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** E1: сколько шаблонов можно закрепить — строка над клавиатурой не должна разрастаться. */
const val PINNED_ENTRIES_LIMIT = 8

/**
 * E1: закреплённые шаблоны быстрого ввода. Это не новая сущность БД, а список тех же [FrequentEntry]
 * (счёт + категория + сумма), поэтому баланс не хранится, а применение шаблона лишь заполняет форму.
 * Хранилище — на конкретном устройстве (как остальные UI-настройки): в бэкап и синхронизацию не входит.
 */
interface PinnedEntries {
    /** Закреплённые шаблоны в порядке закрепления (раньше закреплён — левее). */
    val pinned: StateFlow<List<FrequentEntry>>

    /**
     * Закрепляет шаблон или снимает закрепление. Возвращает false, если закрепить нельзя —
     * достигнут [PINNED_ENTRIES_LIMIT]; снятие всегда проходит.
     */
    fun toggle(entry: FrequentEntry): Boolean
}

/** Реализация в памяти: тесты и значение по умолчанию, пока UI не передал постоянное хранилище. */
class InMemoryPinnedEntries(initial: List<FrequentEntry> = emptyList()) : PinnedEntries {
    private val state = MutableStateFlow(initial.distinct().take(PINNED_ENTRIES_LIMIT))
    override val pinned: StateFlow<List<FrequentEntry>> = state

    override fun toggle(entry: FrequentEntry): Boolean {
        var accepted = true
        state.update { current ->
            when {
                entry in current -> current - entry
                current.size >= PINNED_ENTRIES_LIMIT -> current.also { accepted = false }
                else -> current + entry
            }
        }
        return accepted
    }
}

/** Закреплённый или частый шаблон для строки над клавиатурой. */
data class QuickEntrySlot(val entry: FrequentEntry, val isPinned: Boolean)

/**
 * E1: сначала закреплённые (в порядке закрепления), затем частые без дублей закреплённых.
 * Закреплённый шаблон остаётся, даже если уже не входит в частые; чужие дубли не множатся.
 */
fun mergePinnedAndFrequent(pinned: List<FrequentEntry>, frequent: List<FrequentEntry>): List<QuickEntrySlot> {
    val pinnedSet = pinned.toSet()
    return pinned.distinct().map { QuickEntrySlot(it, isPinned = true) } +
        frequent.filterNot { it in pinnedSet }.map { QuickEntrySlot(it, isPinned = false) }
}

/** Устойчивая к мусору запись шаблона для SharedPreferences: `kind|account|category|amount|currency`. */
object PinnedEntryCodec {
    private const val SEP = '|'

    fun encode(entry: FrequentEntry): String? {
        val parts = listOf(entry.kind.name, entry.accountId, entry.categoryId.orEmpty(), entry.amountMinor.toString(), entry.currencyCode)
        if (parts.any { SEP in it || '\n' in it }) return null
        return parts.joinToString(SEP.toString())
    }

    /** Повреждённая или неизвестная запись → null (настройка не должна ронять экран ввода). */
    fun decode(text: String): FrequentEntry? {
        val p = text.split(SEP)
        if (p.size != 5) return null
        val kind = FrequentEntryKind.entries.firstOrNull { it.name == p[0] } ?: return null
        val amount = p[3].toLongOrNull() ?: return null
        if (p[1].isEmpty() || p[4].isEmpty()) return null
        return FrequentEntry(kind, p[1], p[2].ifEmpty { null }, amount, p[4])
    }
}
