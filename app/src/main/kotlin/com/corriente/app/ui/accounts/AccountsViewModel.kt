package com.corriente.app.ui.accounts

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.app.R
import com.corriente.app.ui.categories.CategoryPalette
import com.corriente.app.ui.common.WritingViewModel
import com.corriente.app.ui.common.blockedMessage
import com.corriente.data.repository.DeleteResult
import com.corriente.app.ui.common.uiMessage
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.model.Account
import com.corriente.data.repository.AccountRepository
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import java.time.LocalDate
import com.corriente.data.usecase.AccountBalanceUseCase
import com.corriente.data.usecase.accountBalance
import kotlinx.coroutines.flow.first
import com.corriente.data.usecase.totalsByCurrency
import com.corriente.money.AmountInput
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Счёт вместе с его валютой — [Currency] нужна для форматирования сумм (MoneyFormatter). */
data class AccountRow(val account: Account, val currency: Currency, val balance: Money? = null)

/**
 * Счета одной валюты и итог по ней (T1.7). [total] = null, если ни один счёт валюты не помечен
 * `include_in_total`. Разные валюты — разные группы; складывать их между собой невозможно (I-8).
 */
data class CurrencyBalanceGroup(
    val currency: Currency,
    val rows: List<AccountRow>,
    val total: Money?,
)

/** UI-2.5: карточка итога по одной валюте — берётся прямо из группы, отдельных вычислений нет (I-8). */
data class CurrencyCard(val code: String, val symbol: String, val currency: Currency, val total: Money?)

fun currencyCards(groups: List<CurrencyBalanceGroup>): List<CurrencyCard> =
    groups.map { CurrencyCard(it.currency.code.code, it.currency.symbol, it.currency, it.total) }

data class AccountsUiState(
    val groups: List<CurrencyBalanceGroup> = emptyList(),
    val archived: List<AccountRow> = emptyList(),
    val activeCurrencies: List<Currency> = emptyList(),
)

/** Форма создания/редактирования счёта: значения ровно как их ввёл пользователь. */
data class AccountForm(
    val name: String,
    val currency: CurrencyCode,
    val kind: AccountKind,
    val openingBalanceText: String,
    val includeInTotal: Boolean,
    val color: Int = CategoryPalette.first(),
    val icon: String? = null,
)

/**
 * Открытый редактор счёта. [editingId] = null — создание нового. [currencyLocked] = у счёта
 * уже есть операции: валюта и начальный остаток стали историческими фактами, поля недоступны (I-23).
 */
data class AccountEditor(
    val editingId: String?,
    val currencyLocked: Boolean,
    val name: String,
    val currency: CurrencyCode?,
    val kind: AccountKind,
    val openingBalanceText: String,
    val includeInTotal: Boolean,
    val color: Int,
    val icon: String,
)

private fun fallbackCurrency(code: CurrencyCode): Currency =
    Currency(code, minorUnits = 2, displayScale = 2, symbol = code.code)

private fun Account.toRow(currenciesByCode: Map<String, Currency>): AccountRow =
    AccountRow(this, currenciesByCode[currency.code] ?: fallbackCurrency(currency))

/**
 * Разбор поля «начальный остаток». Величина — через инвариант-безопасный [AmountInput] (I-25),
 * знак — отдельно: счёт-долг стартует с отрицательного остатка, а [AmountInput] по контракту
 * всегда неотрицателен.
 */
internal fun openingBalanceMinor(text: String, currency: Currency): Minor {
    val normalized = text.trim().replace('−', '-').replace('–', '-')
    val negative = normalized.startsWith("-")
    val magnitude = AmountInput.fromText(normalized, currency).toMinorOrNull(currency)?.raw ?: 0L
    return Minor(if (negative) -magnitude else magnitude)
}

/**
 * Минорные единицы → строка для поля ввода ("1234.56", без группировки и символа), такая, что
 * [openingBalanceMinor] разбирает её обратно в то же число. Отрицательный остаток сохраняет знак.
 */
internal fun openingBalanceText(minor: Minor, currency: Currency): String {
    if (minor.raw == 0L) return ""
    val negative = minor.raw < 0
    val digits = (if (negative) -minor.raw else minor.raw).toString().padStart(currency.minorUnits + 1, '0')
    val body = if (currency.minorUnits == 0) {
        digits
    } else {
        "${digits.dropLast(currency.minorUnits)}.${digits.takeLast(currency.minorUnits)}"
    }
    return if (negative) "-$body" else body
}

/** Поле суммы осмысленно, только если в нём есть цифра: «-», «abc» не должны превращаться в тихий ноль. */
internal fun hasAmountDigits(text: String): Boolean = text.any { it in '0'..'9' }

/**
 * Корректировка баланса, которую сейчас вводит пользователь: счёт, его валюта и расчётный остаток **на сегодня**
 * ([balance]). Операции с будущей датой в нём не учтены — их сумма в [upcoming] (иначе поправка «съела» бы их).
 */
data class AdjustTarget(val account: Account, val currency: Currency, val balance: Money, val upcoming: Money? = null)

class AccountsViewModel(
    private val accounts: AccountRepository,
    private val currencies: CurrencyRepository,
    balances: AccountBalanceUseCase,
    private val txns: TxnRepository? = null,
    private val today: () -> LocalDate = LocalDate::now,
) : WritingViewModel() {

    val uiState: StateFlow<AccountsUiState> = combine(
        balances.observeBalances(),
        accounts.observeArchived(),
        currencies.observeAll(),
        currencies.observeActive(),
    ) { accountBalances, archived, allCurrencies, activeCurrencies ->
        val byCode = allCurrencies.associateBy { it.code.code }
        val totals = totalsByCurrency(accountBalances)
        val groups = accountBalances.groupBy { it.account.currency }.entries
            .sortedBy { it.key.code }
            .map { (code, group) ->
                val currency = byCode[code.code] ?: fallbackCurrency(code)
                CurrencyBalanceGroup(
                    currency = currency,
                    rows = group.sortedBy { it.account.displayOrder }
                        .map { AccountRow(it.account, currency, it.balance) },
                    total = totals[code],
                )
            }
        AccountsUiState(
            groups = groups,
            archived = archived.map { it.toRow(byCode) },
            activeCurrencies = activeCurrencies,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    private val _editor = MutableStateFlow<AccountEditor?>(null)
    val editor: StateFlow<AccountEditor?> = _editor

    fun startCreate() {
        _editor.value = AccountEditor(
            editingId = null,
            currencyLocked = false,
            name = "",
            currency = uiState.value.activeCurrencies.firstOrNull()?.code,
            kind = AccountKind.CASH,
            openingBalanceText = "",
            includeInTotal = true,
            color = CategoryPalette.first(),
            icon = "",
        )
    }

    fun startEdit(account: Account) {
        viewModelScope.launch {
            val currency = currencies.getByCode(account.currency) ?: fallbackCurrency(account.currency)
            _editor.value = AccountEditor(
                editingId = account.id,
                currencyLocked = accounts.isInUse(account.id),
                name = account.name,
                currency = account.currency,
                kind = account.kind,
                openingBalanceText = openingBalanceText(account.openingBalance.amount, currency),
                includeInTotal = account.includeInTotal,
                color = account.color.takeIf { it != 0 } ?: CategoryPalette.first(),
                icon = account.icon.orEmpty(),
            )
        }
    }

    fun closeEditor() {
        _editor.value = null
    }

    private val _adjust = MutableStateFlow<AdjustTarget?>(null)
    val adjust: StateFlow<AdjustTarget?> = _adjust

    /** Открыть «Корректировку баланса» для счёта; остаток берётся из уже посчитанных балансов. */
    fun startAdjust(accountId: String) {
        val row = uiState.value.groups.flatMap { it.rows }.firstOrNull { it.account.id == accountId } ?: return
        val total = row.balance ?: return
        val repo = txns
        viewModelScope.launch {
            // Остаток на сегодня = полный остаток минус движения с датой позже сегодняшней.
            val future = if (repo == null) {
                emptyList()
            } else {
                repo.observeRange(today().plusDays(1), LocalDate.of(9999, 12, 31)).first()
            }
            val zeroBased = row.account.copy(openingBalance = Money(Minor(0), row.account.currency))
            val upcoming = accountBalance(zeroBased, future)
            val balance = if (upcoming.amount.raw == 0L) total else total - upcoming
            _adjust.value = AdjustTarget(row.account, row.currency, balance, upcoming.takeIf { it.amount.raw != 0L })
        }
    }

    fun closeAdjust() {
        _adjust.value = null
    }

    /**
     * Записать корректировку так, чтобы остаток счёта стал [actualText] (поле «фактический остаток», знак «−» —
     * долг). Разница с расчётным остатком сохраняется как отдельная запись, не доход и не расход.
     * @return false, если число не разобрано; совпадающий остаток просто закрывает диалог.
     */
    fun saveAdjust(actualText: String): Boolean {
        val target = _adjust.value ?: return false
        val repo = txns ?: return false
        if (!hasAmountDigits(actualText)) return false
        val actual = openingBalanceMinor(actualText, target.currency)
        val delta = Math.subtractExact(actual.raw, target.balance.amount.raw) // I-3
        if (delta == 0L) {
            _adjust.value = null
            return true
        }
        launchWrite(
            onError = { uiMessage(R.string.adjust_error_save) },
            onSuccess = { _adjust.value = null },
        ) {
            repo.addAdjustment(target.account.id, Money(Minor(delta), target.currency.code), today())
        }
        return true
    }

    /** @return false, если форма невалидна (пустое имя или не выбрана валюта) — редактор остаётся открыт. */
    fun save(form: AccountForm): Boolean {
        if (form.name.isBlank()) return false
        val editor = _editor.value

        launchWrite(
            onError = { uiMessage(R.string.accounts_error_save) },
            onSuccess = { _editor.value = null },
        ) {
            val currency = currencies.getByCode(form.currency) ?: fallbackCurrency(form.currency)
            val money = Money(openingBalanceMinor(form.openingBalanceText, currency), form.currency)
            val id = editor?.editingId
            val icon = form.icon?.trim()?.ifBlank { null }
            if (id == null) {
                accounts.create(
                    name = form.name.trim(),
                    currency = form.currency,
                    kind = form.kind,
                    openingBalance = money,
                    color = form.color,
                    icon = icon,
                    includeInTotal = form.includeInTotal,
                )
            } else {
                accounts.update(id, form.name.trim(), form.color, icon, form.includeInTotal)
                if (editor.currencyLocked.not()) {
                    // Гонка: пока редактор был открыт, по счёту могла появиться операция —
                    // тогда валюта уже исторический факт (I-23), меняем только имя и флаги.
                    if (accounts.isInUse(id)) {
                        postMessage(R.string.accounts_error_currency_fixed)
                    } else {
                        accounts.setCurrencyAndOpeningBalanceBeforeFirstUse(id, form.currency, money)
                    }
                }
            }
        }
        return true
    }

    fun archive(id: String) {
        launchWrite(onError = { uiMessage(R.string.accounts_error_archive) }) { accounts.archive(id) }
    }

    fun unarchive(id: String) {
        launchWrite(onError = { uiMessage(R.string.accounts_error_unarchive) }) { accounts.unarchive(id) }
    }

    /** A2.2: закрывает редактор только при удалении; отказ репозитория — сообщение с причиной. */
    fun deleteIfUnused(id: String) {
        launchWrite(onError = { uiMessage(R.string.accounts_error_delete) }) {
            when (val result = accounts.deleteIfUnused(id)) {
                DeleteResult.Deleted, DeleteResult.NotFound -> closeEditor()
                is DeleteResult.Blocked -> postMessage(result.reasons.blockedMessage(forAccount = true))
            }
        }
    }

    companion object {
        fun factory(
            accounts: AccountRepository,
            currencies: CurrencyRepository,
            balances: AccountBalanceUseCase,
            txns: TxnRepository? = null,
        ) = viewModelFactory {
            initializer { AccountsViewModel(accounts, currencies, balances, txns) }
        }
    }
}
