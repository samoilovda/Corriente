package com.corriente.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.corriente.data.db.entity.CurrencyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(currencies: List<CurrencyEntity>)

    @Update
    suspend fun update(currency: CurrencyEntity)

    @Query("SELECT * FROM currency ORDER BY display_order, code")
    fun observeAll(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currency WHERE is_active = 1 ORDER BY display_order, code")
    fun observeActive(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currency WHERE code = :code")
    suspend fun getByCode(code: String): CurrencyEntity?
}
