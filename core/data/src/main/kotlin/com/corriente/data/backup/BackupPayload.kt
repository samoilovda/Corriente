package com.corriente.data.backup

import kotlinx.serialization.Serializable

/**
 * Формат файла бэкапа (T1.9, ARCHITECTURE.md §3.3, I-21). Плоские @Serializable-DTO, а не
 * Room-сущности напрямую: Room-аннотации и kotlinx.serialization в одном классе — лишнее
 * смешение слоёв ради экономии семи одинаковых классов. Перечисления хранятся как имена
 * (строка), не порядковый номер — устойчиво к добавлению нового варианта enum в будущем.
 *
 * [schemaVersion] — версия схемы Room на момент экспорта (I-21): восстановление из бэкапа
 * более старой версии обязано пройти по той же цепочке миграций, что и обычное обновление
 * приложения, а не быть отдельным путём с собственными правилами.
 */
@Serializable
data class BackupPayload(
    val schemaVersion: Int,
    val exportedAt: Long,
    val currencies: List<CurrencyBackup>,
    val accounts: List<AccountBackup>,
    val categories: List<CategoryBackup>,
    val transactions: List<TxnBackup>,
    val importBatches: List<ImportBatchBackup>,
    val importAliases: List<ImportAliasBackup>,
    val appSettings: List<AppSettingBackup>,
)

@Serializable
data class CurrencyBackup(
    val code: String,
    val minorUnits: Int,
    val displayScale: Int,
    val symbol: String,
    val isActive: Boolean,
    val displayOrder: Int,
)

@Serializable
data class AccountBackup(
    val id: String,
    val name: String,
    val currencyCode: String,
    val kind: String,
    val openingBalanceMinor: Long,
    val color: Int,
    val icon: String?,
    val displayOrder: Int,
    val isArchived: Boolean,
    val includeInTotal: Boolean,
)

@Serializable
data class CategoryBackup(
    val id: String,
    val name: String,
    val kind: String,
    val parentId: String?,
    val color: Int,
    val icon: String?,
    val origin: String,
    val displayOrder: Int,
    val isArchived: Boolean,
    /** Схема v2 (F1.5). Старые файлы без поля → null. */
    val importBatchId: String? = null,
)

@Serializable
data class TxnBackup(
    val id: String,
    val kind: String,
    val date: String,
    val createdAt: Long,
    val updatedAt: Long,
    val accountId: String,
    val amountMinor: Long,
    val currencyCode: String,
    val toAccountId: String?,
    val toAmountMinor: Long?,
    val toCurrencyCode: String?,
    val categoryId: String?,
    val note: String?,
    val importBatchId: String?,
    val importHash: String?,
)

@Serializable
data class ImportBatchBackup(
    val id: String,
    val sourceApp: String,
    val fileName: String,
    val importedAt: Long,
    val rowCount: Int,
    val reportJson: String,
)

@Serializable
data class ImportAliasBackup(
    val sourceApp: String,
    val kind: String,
    val sourceValue: String,
    val targetId: String,
)

@Serializable
data class AppSettingBackup(val key: String, val value: String)

/**
 * Контрольные суммы бэкапа (R1.4): счётчики + сумма движений по каждой валюте (без переводов,
 * I-11). Не `@Serializable` — считается на лету для сравнения «файл vs текущая БД», в файл не
 * пишется.
 */
data class BackupSummary(
    val accounts: Int,
    val categories: Int,
    val transactions: Int,
    val sumsByCurrency: Map<String, Long>,
)
