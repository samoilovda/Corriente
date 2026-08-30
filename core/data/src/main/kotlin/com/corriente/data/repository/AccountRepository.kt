package com.corriente.data.repository

import com.corriente.data.db.dao.AccountDao
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.model.Account
import com.corriente.data.model.toDomain
import com.corriente.data.model.toEntity
import com.corriente.money.CurrencyCode
import com.corriente.money.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Enforced здесь, а не в Room (T0.4: у Room 2.8 нет проверенного API для @Entity-level CHECK):
 *  - I-23: валюта счёта неизменна после первой операции по нему.
 *  - I-1/I-2: [Account.openingBalance] — Money той же валюты, что и счёт (проверяется в
 *    [com.corriente.data.model.toEntity], здесь дополнительно на входе конструктора).
 */
class AccountRepository(private val dao: AccountDao) {

    fun observeActive(): Flow<List<Account>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Account? = dao.getById(id)?.toDomain()

    suspend fun create(
        name: String,
        currency: CurrencyCode,
        kind: AccountKind,
        openingBalance: Money,
        color: Int,
        icon: String? = null,
    ): Account {
        require(openingBalance.currency == currency) {
            "Opening balance currency ${openingBalance.currency} does not match $currency"
        }
        val account = Account(
            id = UUID.randomUUID().toString(),
            name = name,
            currency = currency,
            kind = kind,
            openingBalance = openingBalance,
            color = color,
            icon = icon,
            displayOrder = 0,
            isArchived = false,
            includeInTotal = true,
        )
        dao.insert(account.toEntity())
        return account
    }

    /** Переименование, смена иконки/цвета/участия в итоге — валюту и остаток этим методом не тронуть. */
    suspend fun rename(id: String, name: String, color: Int, icon: String?, includeInTotal: Boolean) {
        val existing = requireNotNull(dao.getById(id)) { "Account $id not found" }
        dao.update(existing.copy(name = name, color = color, icon = icon, includeInTotal = includeInTotal))
    }

    /**
     * I-23: разрешено только пока у счёта нет ни одной операции. Смена валюты после первой
     * операции — не UI-ограничение, а инвариант; отказ здесь работает независимо от того,
     * позволяет ли экран редактирования нажать на поле валюты.
     */
    suspend fun changeCurrencyBeforeFirstUse(id: String, newCurrency: CurrencyCode, newOpeningBalance: Money) {
        require(newOpeningBalance.currency == newCurrency) {
            "Opening balance currency ${newOpeningBalance.currency} does not match $newCurrency"
        }
        check(!dao.hasTransactions(id)) {
            "Cannot change currency of account $id: it already has transactions (I-23)"
        }
        val existing = requireNotNull(dao.getById(id)) { "Account $id not found" }
        dao.update(
            existing.copy(
                currencyCode = newCurrency.code,
                openingBalanceMinor = newOpeningBalance.amount.raw,
            )
        )
    }

    /** Архивирование — единственный способ убрать счёт из активных, если у него есть операции. */
    suspend fun archive(id: String) {
        val existing = requireNotNull(dao.getById(id)) { "Account $id not found" }
        dao.update(existing.copy(isArchived = true))
    }

    suspend fun unarchive(id: String) {
        val existing = requireNotNull(dao.getById(id)) { "Account $id not found" }
        dao.update(existing.copy(isArchived = false))
    }

    /** Физическое удаление разрешено только для счёта без единой операции — иначе см. [archive]. */
    suspend fun deleteIfUnused(id: String): Boolean {
        if (dao.hasTransactions(id)) return false
        val existing = dao.getById(id) ?: return false
        dao.delete(existing)
        return true
    }
}
