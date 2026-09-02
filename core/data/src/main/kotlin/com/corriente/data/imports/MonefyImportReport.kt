package com.corriente.data.imports

import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

/**
 * Сводка того, что реально записал импорт — сериализуется в `import_batch.report_json` (F1.5),
 * чтобы экран «История импортов» мог показать её и предложить откат. Раньше туда всегда
 * писалось `"{}"`.
 */
@Serializable
data class MonefyImportReport(
    val accounts: Int = 0,
    val categories: Int = 0,
    val operations: Int = 0,
    val transfers: Int = 0,
    val unpairedHalves: Int = 0,
    val reviews: Int = 0,
    val errors: Int = 0,
) {
    fun encode(): String = JSON.encodeToString(serializer(), this)

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        /** Разобрать `report_json`; для старых батчей с `"{}"` вернёт нули. */
        fun decode(json: String): MonefyImportReport =
            runCatching { JSON.decodeFromString(serializer(), json) }.getOrDefault(MonefyImportReport())
    }
}
