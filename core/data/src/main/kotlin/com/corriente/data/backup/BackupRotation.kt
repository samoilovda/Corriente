package com.corriente.data.backup

/**
 * Общая логика ротации бэкапов (T5.1): и файловые копии БД перед миграцией (I-20), и
 * периодический автобэкап держат только последние [keep] штук.
 *
 * Имена обязаны содержать метку времени в формате, где лексикографический порядок совпадает
 * с хронологическим (`yyyyMMdd-HHmmss`), тогда сортировка строк = сортировка по времени.
 */
fun namesToPrune(existing: List<String>, keep: Int): List<String> {
    require(keep >= 1) { "keep must be >= 1, got $keep" }
    if (existing.size <= keep) return emptyList()
    return existing.sorted().dropLast(keep)
}

const val AUTO_BACKUP_PREFIX = "corriente-backup-"
const val AUTO_BACKUP_SUFFIX = ".json"
const val DEFAULT_BACKUP_RETENTION = 7
