package com.corriente.data.backup

/** Порог «бэкапа давно не было» (R1.5) — константа в коде, не настройка. */
const val BACKUP_WARNING_THRESHOLD_DAYS: Long = 14
const val BACKUP_WARNING_THRESHOLD_MS: Long = BACKUP_WARNING_THRESHOLD_DAYS * 24 * 60 * 60 * 1000

/** Ниже этого числа операций плашка не показывается вовсе — пустой базе бэкап не горит. */
const val BACKUP_WARNING_MIN_TXN_COUNT: Int = 50

/**
 * Показывать ли плашку «Последний бэкап: N дней назад» (R1.5). Чистая функция — параметры
 * приходят уже посчитанными (config автобэкапа, число операций, текущее время), никакого Room
 * внутри, поэтому тестируется без БД.
 *
 * Правило (ROADMAP.md, R1.5): плашка нужна, если операций больше [BACKUP_WARNING_MIN_TXN_COUNT]
 * **и** (автобэкап выключен, либо последний запуск старше [BACKUP_WARNING_THRESHOLD_DAYS] дней,
 * либо его не было ни разу). Условие "операций больше 50" относится к обеим веткам: при малом
 * числе операций плашки нет, даже если автобэкап выключен.
 */
fun shouldWarnAboutBackup(lastRunAt: Long?, enabled: Boolean, txnCount: Int, now: Long): Boolean {
    if (txnCount <= BACKUP_WARNING_MIN_TXN_COUNT) return false
    if (!enabled) return true
    val ageMs = lastRunAt?.let { now - it } ?: return true
    return ageMs > BACKUP_WARNING_THRESHOLD_MS
}
