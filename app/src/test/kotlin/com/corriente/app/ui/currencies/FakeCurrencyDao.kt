package com.corriente.app.ui.currencies

import com.corriente.data.db.dao.CurrencyDao
import com.corriente.data.db.entity.CurrencyEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Ручная реализация [CurrencyDao] на in-memory списке — [CurrencyDao] это обычный интерфейс,
 * фейк дешевле Robolectric и in-memory Room и держит тест ViewModel чисто-JVM.
 */
class FakeCurrencyDao(initial: List<CurrencyEntity>) : CurrencyDao {

    private val rows = MutableStateFlow(initial.sortedWith(compareBy({ it.displayOrder }, { it.code })))

    override suspend fun insertAll(currencies: List<CurrencyEntity>) {
        val known = rows.value.map { it.code }.toSet()
        rows.value = (rows.value + currencies.filter { it.code !in known })
            .sortedWith(compareBy({ it.displayOrder }, { it.code }))
    }

    override suspend fun update(currency: CurrencyEntity) {
        rows.value = rows.value.map { if (it.code == currency.code) currency else it }
    }

    override fun observeAll(): Flow<List<CurrencyEntity>> = rows

    override fun observeActive(): Flow<List<CurrencyEntity>> = rows.map { list -> list.filter { it.isActive } }

    override suspend fun getByCode(code: String): CurrencyEntity? = rows.value.firstOrNull { it.code == code }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}
