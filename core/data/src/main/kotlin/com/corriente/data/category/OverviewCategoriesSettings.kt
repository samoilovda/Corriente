package com.corriente.data.category

import com.corriente.data.db.dao.AppSettingDao
import com.corriente.data.db.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Какие категории расходов показывать вокруг кольца на главном (до [MAX]). Пустой список — «автоматически»:
 * сначала те, где были траты, затем по порядку категорий. Хранится в `app_setting` (без миграции схемы, как
 * [com.corriente.data.applock.AppLockSettings]) и переезжает с бэкапом — id категорий в бэкапе те же.
 */
class OverviewCategoriesSettings(private val dao: AppSettingDao) {

    val pinned: Flow<List<String>> = dao.observe(KEY).map(::decode)

    suspend fun current(): List<String> = decode(dao.get(KEY))

    /** Пустой список возвращает автоматический выбор. */
    suspend fun set(ids: List<String>) = dao.set(AppSettingEntity(KEY, ids.distinct().take(MAX).joinToString(SEPARATOR)))

    companion object {
        const val MAX = 12
        private const val KEY = "overview.categories"
        private const val SEPARATOR = ","

        internal fun decode(raw: String?): List<String> =
            raw?.split(SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    }
}
