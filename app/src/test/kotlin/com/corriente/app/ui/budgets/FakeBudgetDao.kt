package com.corriente.app.ui.budgets

import com.corriente.data.db.dao.BudgetDao
import com.corriente.data.db.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [BudgetDao] для юнит-тестов ViewModel (R2.3). */
class FakeBudgetDao : BudgetDao {

    val rows = MutableStateFlow<List<BudgetEntity>>(emptyList())

    override suspend fun insert(budget: BudgetEntity) {
        rows.value = rows.value + budget
    }

    override suspend fun update(budget: BudgetEntity) {
        rows.value = rows.value.map { if (it.id == budget.id) budget else it }
    }

    override suspend fun delete(budget: BudgetEntity) {
        rows.value = rows.value.filterNot { it.id == budget.id }
    }

    override fun observeAll(): Flow<List<BudgetEntity>> = rows.map { it.sortedByDescending { b -> b.startsOn } }

    override suspend fun getById(id: String): BudgetEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun getAll(): List<BudgetEntity> = rows.value

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }
}
