package com.corriente.app.ui.fxreport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.data.usecase.conversionCost
import com.corriente.data.usecase.fxDeals
import com.corriente.money.MoneyFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class FxDealUi(
    val txnId: String,
    val dateText: String,
    val rateLabel: String,
    val fromText: String,
    val toText: String,
    val rateMicros: Long,
)

data class FxPairUi(val title: String, val deals: List<FxDealUi>)

/**
 * R3.4: «во что обошлись конвертации в этом году» по паре валют. [insufficientData] —
 * меньше трёх сделок по паре (ADR-013: сравнивать курс не с чем, кроме собственных сделок);
 * тогда [amountText] пуст, экран обязан показать текст «недостаточно данных», а не 0.
 */
data class ConversionCostUi(val title: String, val dealCount: Int, val amountText: String?, val insufficientData: Boolean)

data class FxReportUiState(
    val pairs: List<FxPairUi> = emptyList(),
    val conversionCosts: List<ConversionCostUi> = emptyList(),
    val loaded: Boolean = false,
)

/** T5.4: экран «мои фактические курсы обмена». R3.4 расширяет его оценкой цены отклонений от медианы. */
class FxReportViewModel(
    txns: TxnRepository,
    currencies: CurrencyRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    val uiState: StateFlow<FxReportUiState> = combine(
        txns.observeAll(),
        currencies.observeAll(),
    ) { allTxns, allCurrencies ->
        val byCode = allCurrencies.associateBy { it.code }
        val fallback = { code: com.corriente.money.CurrencyCode ->
            byCode[code] ?: com.corriente.money.Currency(code, 2, 2, code.code)
        }
        val fxPairs = fxDeals(allTxns, byCode)
        val pairs = fxPairs.map { pair ->
            FxPairUi(
                title = "${pair.from.code} → ${pair.to.code}",
                deals = pair.deals.map { deal ->
                    FxDealUi(
                        txnId = deal.txnId,
                        dateText = deal.date.toString(),
                        rateLabel = deal.rateLabel,
                        fromText = MoneyFormatter.format(deal.from, fallback(deal.from.currency)),
                        toText = MoneyFormatter.format(deal.to, fallback(deal.to.currency)),
                        rateMicros = deal.rateMicros,
                    )
                },
            )
        }

        // R3.4: «в этом году» — год из [today]; A→B и B→A уже сведены в одну пару внутри
        // conversionCost (ADR-013: сравниваем только собственные сделки, курсы из сети не берём).
        val thisYear = today().year
        val dealsThisYear = fxPairs.flatMap { it.deals }.filter { it.date.year == thisYear }
        val conversionCosts = conversionCost(dealsThisYear).map { estimate ->
            ConversionCostUi(
                title = "${estimate.baseCurrency.code} ↔ ${estimate.quoteCurrency.code}",
                dealCount = estimate.dealCount,
                amountText = estimate.cost?.let { MoneyFormatter.format(it, fallback(it.currency)) },
                insufficientData = estimate.cost == null,
            )
        }

        FxReportUiState(pairs = pairs, conversionCosts = conversionCosts, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FxReportUiState())

    companion object {
        fun factory(txns: TxnRepository, currencies: CurrencyRepository) = viewModelFactory {
            initializer { FxReportViewModel(txns, currencies) }
        }
    }
}
