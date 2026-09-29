package com.corriente.app.widget

import com.corriente.data.db.entity.AccountKind
import com.corriente.data.model.Account
import com.corriente.data.model.Txn
import com.corriente.data.widget.WidgetConfig
import com.corriente.data.widget.WidgetSnapshot
import com.corriente.money.Currency
import com.corriente.money.CurrencyCode
import com.corriente.money.Minor
import com.corriente.money.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** A1.2: снимок виджета пересчитывается по новой дате при живом процессе. */
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetSnapshotsFlowTest {

    private val rub = CurrencyCode("RUB")
    private val rubMeta = Currency(rub, 2, 2, "₽")
    private val account = Account(
        id = "cash", name = "Наличные", currency = rub, kind = AccountKind.CASH,
        openingBalance = Money(Minor(0), rub), color = 0, icon = null,
        displayOrder = 0, isArchived = false, includeInTotal = true,
    )

    private fun expense(id: String, minor: Long, date: LocalDate) =
        Txn.Expense(id, date, 0, 0, "cash", Money(Minor(minor), rub), null)

    @Test
    fun `after the date rolls over a new write is computed against the new month`() = runTest {
        val date = MutableStateFlow(LocalDate.of(2026, 8, 31))
        val txns = MutableStateFlow(listOf(expense("a", 100_00, LocalDate.of(2026, 8, 31))))
        var latest: WidgetSnapshot? = null

        val job = backgroundScope.launch {
            widgetSnapshots(
                dateTicker = date,
                accounts = flowOf(listOf(account)),
                txnsInRange = { from, to ->
                    txns.map { list -> list.filter { !it.date.isBefore(from) && !it.date.isAfter(to) } }
                },
                deltas = flowOf(emptyMap()),
                currencies = flowOf(listOf(rubMeta)),
                categories = flowOf(emptyList()),
                config = flowOf(WidgetConfig()),
                clock = { 0L },
            ).collect { latest = it }
        }
        runCurrent()
        assertTrue(latest!!.monthExpenses.single().formatted.contains("100.00"))

        // Полночь: новый месяц, затем запись датой нового дня.
        date.value = LocalDate.of(2026, 9, 1)
        runCurrent()
        txns.value = txns.value + expense("b", 70_00, LocalDate.of(2026, 9, 1))
        runCurrent()

        val month = latest!!.monthExpenses.single().formatted
        assertTrue(month, month.contains("70.00") && !month.contains("170"))
        job.cancel()
    }

    @Test
    fun `dateTicker emits only real changes and reacts to the signal`() = runTest {
        var now = LocalDate.of(2026, 8, 31)
        val signal = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val seen = mutableListOf<LocalDate>()
        val job = backgroundScope.launch { dateTicker({ now }, signal, pollMs = 1_000).collect { seen += it } }
        runCurrent()
        advanceTimeBy(3_500)
        assertEquals(listOf(LocalDate.of(2026, 8, 31)), seen)

        now = LocalDate.of(2026, 9, 1)
        signal.tryEmit(Unit)
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 1)), seen)
        job.cancel()
    }
}
