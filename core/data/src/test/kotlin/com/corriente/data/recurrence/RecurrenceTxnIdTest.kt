package com.corriente.data.recurrence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class RecurrenceTxnIdTest {
    @Test
    fun sameRuleAndDateGiveTheSameIdOnEveryDevice() {
        val d = LocalDate.of(2026, 10, 1)
        assertEquals(recurrenceTxnId("rule-1", d), recurrenceTxnId("rule-1", d))
        assertNotEquals(recurrenceTxnId("rule-1", d), recurrenceTxnId("rule-1", d.plusMonths(1)))
        assertNotEquals(recurrenceTxnId("rule-1", d), recurrenceTxnId("rule-2", d))
        // формат — канонический UUID в нижнем регистре, как остальные id (ARCHITECTURE.md, ADR о UUID)
        assertEquals(36, recurrenceTxnId("rule-1", d).length)
    }
}
