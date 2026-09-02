package com.corriente.data.repository

import com.corriente.data.db.dao.BudgetDao
import com.corriente.data.db.entity.BudgetPeriod
import com.corriente.data.model.Budget
import com.corriente.data.model.toDomain
import com.corriente.data.model.toEntity
import com.corriente.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/**
 * Бюджеты по категориям (R2.3). Единственное правило, которое здесь enforced (Room не умеет
 * CHECK — T0.4-стиль, как в [TxnRepository]): сумма бюджета не отрицательна. Строгая
 * принадлежность одной валюте (ADR-012, I-8) гарантирована самим типом [Money] — здесь
 * дополнительно нечего проверять.
 */
class BudgetRepository(private val dao: BudgetDao) {

    fun observeAll(): Flow<List<Budget>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Budget? = dao.getById(id)?.toDomain()

    suspend fun create(
        categoryId: String?,
        amount: Money,
        startsOn: LocalDate,
        period: BudgetPeriod = BudgetPeriod.MONTH,
    ): Budget {
        require(amount.amount.raw >= 0) { "Budget amount must not be negative" }
        val budget = Budget(id = UUID.randomUUID().toString(), categoryId, amount, period, startsOn)
        dao.insert(budget.toEntity())
        return budget
    }

    suspend fun update(id: String, categoryId: String?, amount: Money, startsOn: LocalDate) {
        require(amount.amount.raw >= 0) { "Budget amount must not be negative" }
        val existing = requireNotNull(dao.getById(id)) { "Budget $id not found" }
        dao.update(
            existing.copy(
                categoryId = categoryId,
                currencyCode = amount.currency.code,
                amountMinor = amount.amount.raw,
                startsOn = startsOn.toString(),
            ),
        )
    }

    suspend fun delete(id: String) {
        dao.getById(id)?.let { dao.delete(it) }
    }
}
