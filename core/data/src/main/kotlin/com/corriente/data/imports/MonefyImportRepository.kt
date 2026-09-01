package com.corriente.data.imports

import androidx.room.withTransaction
import com.corriente.data.db.AppDatabase
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CategoryOrigin
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.db.entity.ImportBatchEntity
import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnKind
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.util.UUID

/**
 * T3.4: запись плана импорта одной транзакцией БД и откат батча (MONEFY_IMPORT.md §5 п.7).
 *
 * Идемпотентность (I-19): каждая строка получает `import_hash` = sha256 от натурального ключа
 * плюс индекс повторения внутри группы одинаковых строк — повторный импорт того же файла
 * вставляет 0 новых строк. Откат — удаление батча + чистка осиротевших IMPORT-категорий.
 */
class MonefyImportRepository(private val db: AppDatabase) {

    data class ImportResult(val batchId: String, val inserted: Int, val skipped: Int)

    suspend fun import(plan: MonefyImportPlan, fileName: String, reportJson: String = "{}"): ImportResult {
        val batchId = UUID.randomUUID().toString()
        var inserted = 0
        var skipped = 0

        db.withTransaction {
            db.importBatchDao().insert(
                ImportBatchEntity(
                    id = batchId, sourceApp = SOURCE, fileName = fileName,
                    importedAt = System.currentTimeMillis(),
                    rowCount = plan.plainTxns.size + plan.transfers.size,
                    reportJson = reportJson,
                ),
            )

            // валюты: дозасеиваем те, которых нет (сид ISO мог быть неполным / БД пустой)
            val knownCurrencies = db.currencyDao().observeAll().first().map { it.code }.toSet()
            (plan.accounts.map { it.currency.code } +
                plan.transfers.flatMap { listOf(it.fromCurrency.code, it.toCurrency.code) })
                .toSet()
                .filter { it !in knownCurrencies }
                .forEach { code ->
                    db.currencyDao().insertAll(listOf(CurrencyEntity(code, 2, 2, code, isActive = true)))
                }

            // счета: существующий по имени переиспользуем, иначе создаём
            val accountIdByName = HashMap<String, String>()
            val existingAccounts = db.accountDao().observeAll().first().associateBy { it.name }
            plan.accounts.forEach { planned ->
                val existing = existingAccounts[planned.name]
                accountIdByName[planned.name] = existing?.id ?: run {
                    val id = UUID.randomUUID().toString()
                    db.accountDao().insert(
                        AccountEntity(
                            id = id, name = planned.name, currencyCode = planned.currency.code,
                            kind = AccountKind.CASH, openingBalanceMinor = planned.openingBalanceMinor, color = 0,
                        ),
                    )
                    id
                }
            }

            // категории: alias → существующая (name, kind) → создать origin=IMPORT
            val aliasByValue = db.importAliasDao()
                .getForApp(SOURCE, com.corriente.data.db.entity.ImportAliasKind.CATEGORY)
                .associate { it.sourceValue to it.targetId }
            val existingCategories = db.categoryDao().observeAll().first()
            val categoryId = HashMap<Pair<String, CategoryKind>, String>()
            suspend fun resolveCategory(name: String, kind: CategoryKind): String {
                categoryId[name to kind]?.let { return it }
                aliasByValue[name]?.let { categoryId[name to kind] = it; return it }
                existingCategories.firstOrNull { it.name == name && it.kind == kind }?.let {
                    categoryId[name to kind] = it.id; return it.id
                }
                val id = UUID.randomUUID().toString()
                db.categoryDao().insert(
                    CategoryEntity(id = id, name = name, kind = kind, color = 0, origin = CategoryOrigin.IMPORT),
                )
                categoryId[name to kind] = id
                return id
            }

            val occurrence = HashMap<String, Int>()
            fun hashOf(naturalKey: String): String {
                val occ = occurrence.getOrDefault(naturalKey, 0)
                occurrence[naturalKey] = occ + 1
                return sha256("$SOURCE|$naturalKey|$occ")
            }
            suspend fun insertTxn(entity: TxnEntity, naturalKey: String) {
                val hash = hashOf(naturalKey)
                if (db.txnDao().countByImportHash(hash) > 0) {
                    skipped++
                } else {
                    db.txnDao().insert(entity.copy(importHash = hash, importBatchId = batchId))
                    inserted++
                }
            }

            val now = System.currentTimeMillis()
            plan.plainTxns.forEach { txn ->
                val kind = if (txn.kind == MonefyTxnKind.EXPENSE) CategoryKind.EXPENSE else CategoryKind.INCOME
                insertTxn(
                    TxnEntity(
                        id = UUID.randomUUID().toString(),
                        kind = if (txn.kind == MonefyTxnKind.EXPENSE) TxnKind.EXPENSE else TxnKind.INCOME,
                        date = txn.date.toString(), createdAt = now, updatedAt = now,
                        accountId = accountIdByName.getValue(txn.account),
                        amountMinor = txn.amountMinor, currencyCode = txn.currency.code,
                        categoryId = resolveCategory(txn.category, kind),
                    ),
                    txn.naturalKey,
                )
            }
            plan.transfers.forEach { t ->
                insertTxn(
                    TxnEntity(
                        id = UUID.randomUUID().toString(), kind = TxnKind.TRANSFER,
                        date = t.date.toString(), createdAt = now, updatedAt = now,
                        accountId = accountIdByName.getValue(t.fromAccount),
                        amountMinor = t.fromAmountMinor, currencyCode = t.fromCurrency.code,
                        toAccountId = accountIdByName.getValue(t.toAccount),
                        toAmountMinor = t.toAmountMinor, toCurrencyCode = t.toCurrency.code,
                        categoryId = null,
                    ),
                    t.naturalKey,
                )
            }
        }
        return ImportResult(batchId, inserted, skipped)
    }

    /** Откат: удалить все операции батча, осиротевшие IMPORT-категории и сам батч. */
    suspend fun rollback(batchId: String) {
        db.withTransaction {
            db.txnDao().deleteByImportBatch(batchId)
            db.importBatchDao().deleteOrphanedImportCategories()
            db.importBatchDao().getAll().firstOrNull { it.id == batchId }?.let { db.importBatchDao().delete(it) }
        }
    }

    private companion object {
        const val SOURCE = "MONEFY"

        fun sha256(s: String): String =
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
