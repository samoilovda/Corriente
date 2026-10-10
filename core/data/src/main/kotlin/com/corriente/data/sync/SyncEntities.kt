package com.corriente.data.sync

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Состояние синхронизации этого устройства (одна строка, `id = 1`). Её читают триггеры захвата изменений
 * ([SyncTriggers]): пишут в [SyncOutboxEntity] только при `enabled = 1 AND applying = 0`. Нет строки — синхронизация
 * не включалась, триггеры молчат. Идентичность устройства и часы (`sync.device_id`, `sync.hlc`) — в `app_setting`
 * (привязаны к устройству, в бэкап не переносятся).
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 1,
    val enabled: Boolean,
    /** 1 — сейчас применяются чужие изменения: их не надо снова выкладывать. */
    val applying: Boolean = false,
)

/**
 * Что изменилось локально с последней выгрузки: (таблица, ключ). Содержимое строки берётся в момент выгрузки, поэтому
 * несколько правок одной строки схлопываются в одну запись (`INSERT OR REPLACE` в триггере). Строки нет в таблице —
 * значит, её удалили (выгружается надгробие).
 */
@Entity(tableName = "sync_outbox", primaryKeys = ["table_name", "pk"])
data class SyncOutboxEntity(
    @ColumnInfo(name = "table_name") val tableName: String,
    val pk: String,
)

/**
 * Версия каждой синхронизированной строки (HLC, [Hlc.encode]) и надгробие удалённой (`deleted = 1`, данные строки не
 * хранятся — I-22). По ней LWW решает, чья версия новее. Сами таблицы данных служебных столбцов не получают.
 */
@Entity(tableName = "sync_row_meta", primaryKeys = ["table_name", "pk"])
data class SyncRowMetaEntity(
    @ColumnInfo(name = "table_name") val tableName: String,
    val pk: String,
    val hlc: String,
    val deleted: Boolean,
)

/**
 * Категория [fromId] слита в [intoId] (слияние на одном из устройств или совпадение имён новых категорий — §4
 * docs/SYNC_DESIGN.md). Синхронизируется как обычная таблица; операции и бюджеты, пришедшие позже со ссылкой на
 * [fromId], переводятся на [intoId].
 */
@Entity(tableName = "category_redirect")
data class CategoryRedirectEntity(
    @PrimaryKey @ColumnInfo(name = "from_id") val fromId: String,
    @ColumnInfo(name = "into_id") val intoId: String,
)

/**
 * Чужое изменение, которое пока нельзя применить: строка ссылается на счёт/категорию/валюту, которых ещё нет на этом
 * устройстве (придут следующим обменом). [payload] — JSON изменения ([RowChange]). Пробуется снова при каждом обмене.
 */
@Entity(tableName = "sync_deferred", primaryKeys = ["table_name", "pk"])
data class SyncDeferredEntity(
    @ColumnInfo(name = "table_name") val tableName: String,
    val pk: String,
    val hlc: String,
    val payload: String,
)

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun state(): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE id = 1")
    fun observeState(): kotlinx.coroutines.flow.Flow<SyncStateEntity?>

    /** Сколько локальных изменений ждёт выгрузки — по нему планировщик ставит обмен после записи. */
    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observeOutboxCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_deferred")
    fun observeDeferredCount(): kotlinx.coroutines.flow.Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setState(state: SyncStateEntity)

    @Query("SELECT * FROM sync_outbox")
    suspend fun outbox(): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE table_name = :tableName AND pk = :pk")
    suspend fun removeFromOutbox(tableName: String, pk: String)

    @Query("SELECT * FROM sync_row_meta WHERE table_name = :tableName AND pk = :pk")
    suspend fun meta(tableName: String, pk: String): SyncRowMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMeta(meta: SyncRowMetaEntity)

    @Query("SELECT * FROM category_redirect")
    suspend fun categoryRedirects(): List<CategoryRedirectEntity>

    @Query("SELECT * FROM sync_deferred")
    suspend fun deferred(): List<SyncDeferredEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setDeferred(item: SyncDeferredEntity)

    @Query("DELETE FROM sync_deferred WHERE table_name = :tableName AND pk = :pk")
    suspend fun removeDeferred(tableName: String, pk: String)
}
