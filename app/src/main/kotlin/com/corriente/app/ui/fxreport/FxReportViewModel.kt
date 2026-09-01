package com.corriente.app.ui.fxreport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.corriente.data.repository.CurrencyRepository
import com.corriente.data.repository.TxnRepository
import com.corriente.data.usecase.fxDeals
import com.corriente.money.MoneyFormatter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class FxDealUi(
    val txnId: String,
    val dateText: String,
    val rateLabel: String,
    val fromText: String,
    val toText: String,
    val rateMicros: Long,
)

data class FxPairUi(val title: String, val deals: List<FxDealUi>)

data class FxReportUiState(val pairs: List<FxPairUi> = emptyList(), val loaded: Boolean = false)

/** T5.4: экран «мои фактические курсы обмена». */
class FxReportViewModel(
    txns: TxnRepository,
    currencies: CurrencyRepository,
) : ViewModel() {

    val uiState: StateFlow<FxReportUiState> = combine(
        txns.observeAll(),
        currencies.observeAll(),
    ) { allTxns, allCurrencies ->
        val byCode = allCurrencies.associateBy { it.code }
        val fallback = { code: com.corriente.money.CurrencyCode ->
            byCode[code] ?: com.corriente.money.Currency(code, 2, 2, code.code)
        }
        val pairs = fxDeals(allTxns, byCode).map { pair ->
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
        FxReportUiState(pairs = pairs, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FxReportUiState())

    companion object {
        fun factory(txns: TxnRepository, currencies: CurrencyRepository) = viewModelFactory {
            initializer { FxReportViewModel(txns, currencies) }
        }
    }
}
