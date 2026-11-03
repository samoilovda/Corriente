package com.corriente.data.repository

import com.corriente.data.db.buildFtsPrefixQuery
import com.corriente.data.db.nameMatchesQuery
import com.corriente.data.db.dao.AccountDao
import com.corriente.data.db.dao.CategoryDao
import com.corriente.data.db.dao.TxnDao
import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnKind
import com.corriente.data.model.Txn
import com.corriente.data.model.toDomain
import com.corriente.data.model.toEntity
import com.corriente.money.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/**
 * Ядро денежной логики приложения. Здесь и только здесь enforced инварианты, которые Room
 * не проверяет на уровне схемы (T0.4, ARCHITECTURE.md §3.2):
 *  - I-1: amount всегда положителен, знак операции определяется её видом ([Txn.Expense]/
 *    [Txn.Income]/[Txn.Transfer]), а не значением суммы.
 *  - I-15/I-23: валюта операции равна валюте счёта на момент записи.
 *  - I-11: у перевода нет категории, у расхода/дохода нет второго счёта — гарантировано типом
 *    [Txn], здесь дополнительно нечего проверять.
 *  - I-7а: курс перевода — производная от двух сумм, здесь не хранится и не запрашивается.
 */
class TxnRepository(
    private val txnDao: TxnDao,
    private val accountDao: AccountDao,
    /** A2.5: если задан — вид категории проверяется при записи (расход ↔ категория расходов). */
    private val categoryDao: CategoryDao? = null,
) {
    fun observeAll(): Flow<List<Txn>> = txnDao.observeAll().map { list -> list.map { it.toDomain() } }

    /** A1.1: есть ли операции раньше [before] (за границей окна списка). */
    fun observeAnyBefore(before: LocalDate): Flow<Boolean> = txnDao.observeAnyBefore(before.toString())

    /** A2.11: операции одного счёта за диапазон. */
    fun observeForAccountInRange(accountId: String, from: LocalDate, to: LocalDate): Flow<List<Txn>> =
        txnDao.observeForAccountInRange(accountId, from.toString(), to.toString()).map { list -> list.map { it.toDomain() } }

    /** A2.11: сумма движений счёта до [before] (SQL-агрегат), в минорных единицах валюты счёта. */
    fun observeAccountDeltaBefore(accountId: String, before: LocalDate): Flow<Long> =
        txnDao.observeAccountDeltaBefore(accountId, before.toString())

    /** A2.11: только межвалютные переводы (отчёт «курсы обмена»). */
    fun observeCrossCurrencyTransfers(): Flow<List<Txn>> =
        txnDao.observeCrossCurrencyTransfers().map { list -> list.map { it.toDomain() } }

    /** Операции за период (F2.1). [from]/[to] включительно. */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<Txn>> =
        txnDao.observeRange(from.toString(), to.toString()).map { list -> list.map { it.toDomain() } }

    /** Сумма движений по каждому счёту (F2.1) — агрегат из SQL, не скан всей таблицы. */
    fun observeAccountDeltas(): Flow<Map<String, Long>> =
        txnDao.observeAccountDeltas().map { rows -> rows.associate { it.accountId to it.deltaMinor } }

    /** Есть ли в БД хоть одна операция (F2.1/F2.5). */
    fun observeAnyExist(): Flow<Boolean> = txnDao.observeAnyExist()

    /** Счета по убыванию числа операций с даты [since]. */
    suspend fun frequentAccountIds(since: LocalDate, limit: Int = RECENT_ACCOUNTS_LIMIT): List<String> =
        txnDao.frequentAccountIds(since.toString(), limit)

    /** Счета последних внесённых операций, новые первыми (с повторами). */
    suspend fun recentAccountIds(limit: Int = RECENT_ACCOUNTS_LIMIT): List<String> = txnDao.recentAccountIds(limit)

    /** Общее число операций (R1.5 — плашка напоминания о бэкапе). */
    fun observeCount(): Flow<Int> = txnDao.observeCount()

    fun observeForAccount(accountId: String): Flow<List<Txn>> =
        txnDao.observeForAccount(accountId).map { list -> list.map { it.toDomain() } }

    fun observeAdjustments(): Flow<List<Txn>> = txnDao.observeAdjustments().map { list -> list.map { it.toDomain() } }

    /**
     * R2.1: полнотекстовый поиск по всей истории (не ограничен окном `observeRange`) — заметка через FTS4 `MATCH`,
     * название счёта/категории — подстрокой без учёта регистра ([nameMatchesQuery], W11). Пустая строка не должна сюда
     * попадать — вызывающий (`TransactionsViewModel`) переключается на неё только когда есть непустой текст запроса.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun search(query: String): Flow<List<Txn>> {
        val categories = categoryDao?.observeAll() ?: flowOf(emptyList())
        return combine(accountDao.observeAll(), categories) { accounts, cats ->
            accounts.filter { nameMatchesQuery(it.name, query) }.map { it.id } to
                cats.filter { nameMatchesQuery(it.name, query) }.map { it.id }
        }
            .distinctUntilChanged()
            .flatMapLatest { (accountIds, categoryIds) -> txnDao.search(buildFtsPrefixQuery(query), accountIds, categoryIds) }
            .map { list -> list.map { it.toDomain() } }
    }

    suspend fun addExpense(
        accountId: String,
        amount: Money,
        categoryId: String?,
        date: LocalDate,
        note: String? = null,
        /** I-27: вызывающий может задать детерминированный id (повторяющиеся платежи); по умолчанию — случайный. */
        id: String = UUID.randomUUID().toString(),
    ): Txn {
        val now = System.currentTimeMillis()
        val txn = Txn.Expense(
            id = id,
            date = date,
            createdAt = now,
            updatedAt = now,
            accountId = accountId,
            amount = amount,
            categoryId = categoryId,
            note = note,
        )
        val entity = txn.toEntity()
        validate(entity)
        txnDao.insert(entity)
        return txn
    }

    suspend fun addIncome(
        accountId: String,
        amount: Money,
        categoryId: String?,
        date: LocalDate,
        note: String? = null,
        /** I-27: вызывающий может задать детерминированный id (повторяющиеся платежи); по умолчанию — случайный. */
        id: String = UUID.randomUUID().toString(),
    ): Txn {
        val now = System.currentTimeMillis()
        val txn = Txn.Income(
            id = id,
            date = date,
            createdAt = now,
            updatedAt = now,
            accountId = accountId,
            amount = amount,
            categoryId = categoryId,
            note = note,
        )
        val entity = txn.toEntity()
        validate(entity)
        txnDao.insert(entity)
        return txn
    }

    /**
     * Корректировка баланса: [delta] со знаком в валюте счёта — на столько остаток счёта отличался от реального.
     * Не доход и не расход (без категории, мимо отчётов и бюджетов), но хранится как обычная запись и в бэкапе.
     */
    suspend fun addAdjustment(accountId: String, delta: Money, date: LocalDate, note: String? = null): Txn {
        val now = System.currentTimeMillis()
        val txn = Txn.Adjustment(
            id = UUID.randomUUID().toString(),
            date = date,
            createdAt = now,
            updatedAt = now,
            accountId = accountId,
            delta = delta,
            note = note,
        )
        val entity = txn.toEntity()
        validate(entity)
        txnDao.insert(entity)
        return txn
    }

    /**
     * Перевод между счетами, в том числе между валютами (T2.1). [fromAmount]/[toAmount] —
     * единственный источник истины; неявный курс сделки = toAmount / fromAmount, вычисляется
     * на слое ViewModel/UI для показа, здесь не хранится (I-7а, I-12).
     */
    suspend fun addTransfer(
        fromAccountId: String,
        fromAmount: Money,
        toAccountId: String,
        toAmount: Money,
        date: LocalDate,
        note: String? = null,
    ): Txn {
        val now = System.currentTimeMillis()
        val txn = Txn.Transfer(
            id = UUID.randomUUID().toString(),
            date = date,
            createdAt = now,
            updatedAt = now,
            fromAccountId = fromAccountId,
            fromAmount = fromAmount,
            toAccountId = toAccountId,
            toAmount = toAmount,
            note = note,
        )
        val entity = txn.toEntity()
        validate(entity)
        txnDao.insert(entity)
        return txn
    }

    /**
     * Правка перевода (T2.1): те же правила, что при создании — счета разные, обе суммы > 0,
     * валюты равны валютам счетов. Курс сделки по-прежнему нигде не хранится (I-7а/I-12).
     */
    suspend fun updateTransfer(
        id: String,
        fromAccountId: String,
        fromAmount: Money,
        toAccountId: String,
        toAmount: Money,
        date: LocalDate,
        note: String?,
    ): Txn {
        val existing = requireNotNull(txnDao.getById(id)) { "Transaction $id not found" }
        require(existing.kind == TxnKind.TRANSFER) { "$id is not a transfer" }
        val updated = existing.copy(
            accountId = fromAccountId,
            amountMinor = fromAmount.amount.raw,
            currencyCode = fromAmount.currency.code,
            toAccountId = toAccountId,
            toAmountMinor = toAmount.amount.raw,
            toCurrencyCode = toAmount.currency.code,
            date = date.toString(),
            note = note,
            updatedAt = System.currentTimeMillis(),
        )
        validate(updated)
        txnDao.update(updated)
        return updated.toDomain()
    }

    suspend fun getById(id: String): Txn? = txnDao.getById(id)?.toDomain()

    /**
     * Правка расхода/дохода (T1.6): сумма, категория, дата, заметка, счёт. Валюта суммы обязана
     * совпадать с валютой нового счёта (I-15) — при смене счёта на другую валюту вызывающий
     * обязан заново ввести сумму. Перевод и корректировка этим методом не редактируются.
     */
    suspend fun updateEntry(
        id: String,
        accountId: String,
        amount: Money,
        categoryId: String?,
        date: LocalDate,
        note: String?,
        /** Расход ⇄ доход: «записал не тем видом» исправляется правкой. null — вид не меняется. */
        kind: TxnKind? = null,
    ): Txn {
        val existing = requireNotNull(txnDao.getById(id)) { "Transaction $id not found" }
        require(existing.kind == TxnKind.EXPENSE || existing.kind == TxnKind.INCOME) { "Only expense/income are editable here" }
        require(kind == null || kind == TxnKind.EXPENSE || kind == TxnKind.INCOME) { "Entry kind must be expense or income" }
        val newKind = kind ?: existing.kind
        val updated = existing.copy(
            kind = newKind,
            accountId = accountId,
            amountMinor = amount.amount.raw,
            currencyCode = amount.currency.code,
            categoryId = categoryId,
            date = date.toString(),
            note = note,
            updatedAt = System.currentTimeMillis(),
        )
        // Вид категории проверяем только если её меняют: старые операции с «чужой» категорией (до A2.5) можно править.
        validate(updated, checkCategoryKind = existing.categoryId != categoryId || newKind != existing.kind)
        txnDao.update(updated)
        return updated.toDomain()
    }

    suspend fun delete(txn: Txn) {
        txnDao.delete(txn.toEntity())
    }

    suspend fun deleteById(id: String) {
        txnDao.getById(id)?.let { txnDao.delete(it) }
    }

    /**
     * R5.3: вставляет операцию обратно «как есть» — с тем же `id`, `createdAt`/`updatedAt` и
     * `import_hash`, без повторной генерации. Единственный вызывающий — отмена удаления
     * (снекбар «Операция удалена — Отменить»); никакой «корзины» в БД нет (I-22), это просто
     * повторная запись строки, ещё лежащей в памяти ViewModel.
     */
    suspend fun restore(txn: Txn) {
        val entity = txn.toEntity()
        validate(entity, checkCategoryKind = false) // «как было» — ничего нового не вносим
        txnDao.insert(entity)
    }

    /**
     * Единственная проверка правил записи (I-1/I-11/I-15) — та же [requireValidTxn], что и в
     * импорте Monefy (F1.2). Валюты счетов подтягиваются из БД непосредственно перед записью.
     */
    private suspend fun validate(entity: TxnEntity, checkCategoryKind: Boolean = true) {
        val currencyByAccount = listOfNotNull(entity.accountId, entity.toAccountId).distinct()
            .associateWith { accountDao.getById(it)?.currencyCode }
        requireValidTxn(entity) { currencyByAccount[it] }
        val categoryId = entity.categoryId
        if (checkCategoryKind && categoryId != null && categoryDao != null) {
            val kind = categoryDao.getById(categoryId)?.kind
            requireCategoryMatchesKind(entity) { kind }
        }
    }
}

/** Сколько последних операций смотрим, чтобы найти среди них активный счёт (архивные пропускаются). */
const val RECENT_ACCOUNTS_LIMIT = 50
