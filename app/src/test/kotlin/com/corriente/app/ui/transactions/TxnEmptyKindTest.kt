package com.corriente.app.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Test

class TxnEmptyKindTest {

    @Test
    fun `непустой список — без пустого состояния`() {
        assertEquals(TxnEmptyKind.NONE, txnEmptyKind(sectionsEmpty = false, anyExist = true, filterActive = true))
    }

    @Test
    fun `пустая база`() {
        assertEquals(TxnEmptyKind.NO_DATA, txnEmptyKind(sectionsEmpty = true, anyExist = false, filterActive = false))
    }

    @Test
    fun `фильтр ничего не оставил`() {
        assertEquals(TxnEmptyKind.FILTERED, txnEmptyKind(sectionsEmpty = true, anyExist = true, filterActive = true))
    }

    @Test
    fun `без фильтра пусто только в окне — не «ничего не найдено»`() {
        assertEquals(TxnEmptyKind.PERIOD_EMPTY, txnEmptyKind(sectionsEmpty = true, anyExist = true, filterActive = false))
    }
}
