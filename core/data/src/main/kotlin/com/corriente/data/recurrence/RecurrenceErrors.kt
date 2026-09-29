package com.corriente.data.recurrence

import com.corriente.data.db.dao.AppSettingDao
import com.corriente.data.db.entity.AppSettingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A1.5: последняя ошибка материализации по каждому правилу — ключ `recurrence.last_error.<id>` в
 * `app_setting` (без миграции). Пусто — правило исполняется штатно; экран «Повторяющиеся» показывает
 * текст ошибки под строкой правила.
 */
class RecurrenceErrors(private val dao: AppSettingDao) {

    /** id правила → текст последней ошибки. */
    val errors: Flow<Map<String, String>> = dao.observeAll().map { rows ->
        rows.filter { it.key.startsWith(PREFIX) }.associate { it.key.removePrefix(PREFIX) to it.value }
    }

    suspend fun record(ruleId: String, message: String) = dao.set(AppSettingEntity(PREFIX + ruleId, message))

    suspend fun clear(ruleId: String) = dao.delete(PREFIX + ruleId)

    companion object {
        const val PREFIX = "recurrence.last_error."

        /** A3.8: правило пропущено — его счёт в архиве (операции не пишутся в скрытый счёт). Не текст, а код: экран локализует. */
        const val CODE_ARCHIVED_ACCOUNT = "code:archived_account"

        /** A3.8: за один запуск создан максимум [com.corriente.data.recurrence.MAX_CATCH_UP_DATES] операций; остальные — в следующий. */
        const val CODE_CATCH_UP_LIMITED = "code:catch_up_limited"
    }
}
