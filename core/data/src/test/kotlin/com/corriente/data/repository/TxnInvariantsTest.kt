package com.corriente.data.repository

import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnKind
import org.junit.Assert.assertThrows
import org.junit.Test

/** F1.2 — единая проверка правил записи операции (I-1, I-11, I-15). */
class TxnInvariantsTest {

    private val currencies = mapOf("acc" to "RUB", "acc2" to "USD")
    private fun cur(id: String): String? = currencies[id]

    private fun expense() = TxnEntity(
        id = "e", kind = TxnKind.EXPENSE, date = "2026-01-01", createdAt = 0, updatedAt = 0,
        accountId = "acc", amountMinor = 100, currencyCode = "RUB", categoryId = "cat",
    )

    private fun transfer() = TxnEntity(
        id = "t", kind = TxnKind.TRANSFER, date = "2026-01-01", createdAt = 0, updatedAt = 0,
        accountId = "acc", amountMinor = 100, currencyCode = "RUB",
        toAccountId = "acc2", toAmountMinor = 200, toCurrencyCode = "USD", categoryId = null,
    )

    @Test fun `valid expense passes`() = requireValidTxn(expense(), ::cur)
    @Test fun `valid transfer passes`() = requireValidTxn(transfer(), ::cur)

    @Test fun `non-positive amount is rejected (I-1)`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidTxn(expense().copy(amountMinor = 0), ::cur)
        }
    }

    @Test fun `currency must match the account (I-15)`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidTxn(expense().copy(currencyCode = "USD"), ::cur)
        }
    }

    @Test fun `unknown account is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidTxn(expense().copy(accountId = "missing"), ::cur)
        }
    }

    @Test fun `transfer with a category is rejected (I-11)`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidTxn(transfer().copy(categoryId = "cat"), ::cur)
        }
    }

    @Test fun `transfer missing the second side is rejected (I-11)`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidTxn(transfer().copy(toAmountMinor = null), ::cur)
        }
    }

    @Test fun `transfer to-currency must match the destination account (I-15)`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidTxn(transfer().copy(toCurrencyCode = "RUB"), ::cur)
        }
    }

    @Test fun `expense carrying a second side is rejected (I-11)`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidTxn(expense().copy(toAccountId = "acc2"), ::cur)
        }
    }

    @Test fun `transfer into the same account is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            requireValidTxn(transfer().copy(toAccountId = "acc", toCurrencyCode = "RUB"), ::cur)
        }
    }
}
