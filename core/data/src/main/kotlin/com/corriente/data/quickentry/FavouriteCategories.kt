package com.corriente.data.quickentry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Сколько избранных категорий помещается в закреплённую строку (для каждого вида — расходов и доходов — своя). */
const val FAVOURITE_CATEGORIES_LIMIT = 4

/** Настройка «Избранные категории»: включена ли и какие категории выбраны (id, в порядке добавления). */
data class FavouriteSettings(val enabled: Boolean = false, val ids: List<String> = emptyList())

/**
 * Избранные категории экрана ввода — закреплённая строка над сеткой категорий. Не сущность БД: настройка живёт на
 * конкретном устройстве (как закреплённые шаблоны [PinnedEntries]) и в бэкап и синхронизацию не входит.
 * Лимит считается по виду категории: вызывающий передаёт id категорий того же вида.
 */
interface FavouriteCategories {
    val state: StateFlow<FavouriteSettings>

    fun setEnabled(value: Boolean)

    /**
     * Добавляет категорию в избранное. false — в [sameKindIds] уже [FAVOURITE_CATEGORIES_LIMIT] избранных;
     * повторное добавление уже избранной — true без изменений.
     */
    fun add(id: String, sameKindIds: Set<String>): Boolean

    fun remove(id: String)
}

/** Реализация в памяти: тесты и значение по умолчанию, пока UI не передал постоянное хранилище. */
open class InMemoryFavouriteCategories(initial: FavouriteSettings = FavouriteSettings()) : FavouriteCategories {
    private val flow = MutableStateFlow(initial.copy(ids = initial.ids.distinct()))
    override val state: StateFlow<FavouriteSettings> = flow

    override fun setEnabled(value: Boolean) = flow.update { it.copy(enabled = value) }

    override fun add(id: String, sameKindIds: Set<String>): Boolean {
        var accepted = true
        flow.update { current ->
            when {
                id in current.ids -> current
                current.ids.count { it in sameKindIds } >= FAVOURITE_CATEGORIES_LIMIT -> current.also { accepted = false }
                else -> current.copy(ids = current.ids + id)
            }
        }
        return accepted
    }

    override fun remove(id: String) = flow.update { it.copy(ids = it.ids - id) }
}

/** Устойчивая к мусору запись списка id для SharedPreferences: id через `,` (в UUID и `seed-…` запятых нет). */
object FavouriteIdsCodec {
    fun encode(ids: List<String>): String = ids.filter { it.isNotEmpty() && ',' !in it }.joinToString(",")

    fun decode(text: String?): List<String> = text.orEmpty().split(',').filter { it.isNotEmpty() }.distinct()
}

/**
 * Избранные выбранного вида в порядке добавления, не больше [FAVOURITE_CATEGORIES_LIMIT]; id удалённых или
 * архивных категорий (их нет в [kindCategoryIds]) пропускаются.
 */
fun favouritesOfKind(ids: List<String>, kindCategoryIds: Set<String>): List<String> =
    ids.filter { it in kindCategoryIds }.take(FAVOURITE_CATEGORIES_LIMIT)
