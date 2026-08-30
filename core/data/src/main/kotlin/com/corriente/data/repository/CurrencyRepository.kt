package com.corriente.data.repository

import com.corriente.data.db.dao.CurrencyDao
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun CurrencyEntity.toDomain(): Currency =
    Currency(CurrencyCode(code), minorUnits, displayScale, symbol)

/** T1.2: включение/выключение валют из полного сида ISO-4217, правка символа и отображения. */
class CurrencyRepository(private val dao: CurrencyDao) {

    fun observeAll(): Flow<List<Currency>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeActive(): Flow<List<Currency>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    suspend fun getByCode(code: CurrencyCode): Currency? = dao.getByCode(code.code)?.toDomain()

    suspend fun setActive(code: CurrencyCode, active: Boolean) {
        val existing = requireNotNull(dao.getByCode(code.code)) { "Unknown currency ${code.code}" }
        dao.update(existing.copy(isActive = active))
    }

    /** Правка символа/отображаемой точности — данные, не код (I-14, ARCHITECTURE.md §2.1). */
    suspend fun updateDisplay(code: CurrencyCode, symbol: String, displayScale: Int) {
        val existing = requireNotNull(dao.getByCode(code.code)) { "Unknown currency ${code.code}" }
        require(displayScale in 0..existing.minorUnits) {
            "displayScale=$displayScale must not exceed minorUnits=${existing.minorUnits}"
        }
        dao.update(existing.copy(symbol = symbol, displayScale = displayScale))
    }
}
