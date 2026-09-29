package com.corriente.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1.12: по тесту на каждое правило `validate` — всё, что после восстановления уронило бы приложение
 * (`CurrencyCode(...)`, `Currency(...)`, `valueOf`, PRIMARY KEY, уникальные индексы, I-15), ловится заранее.
 */
class BackupValidateTest {

    private fun currency(code: String = "RUB", minor: Int = 2, scale: Int = 2) =
        CurrencyBackup(code, minor, scale, "¤", true, 0)

    private fun account(id: String = "acc", currency: String = "RUB", kind: String = "CASH") =
        AccountBackup(id, "Счёт $id", currency, kind, 0, 0, null, 0, false, true)

    private fun category(
        id: String = "cat", name: String = "Еда $id", kind: String = "EXPENSE", origin: String = "USER", parent: String? = null,
    ) = CategoryBackup(id, name, kind, parent, 0, null, origin, 0, false, null)

    private fun txn(
        id: String = "t1", account: String = "acc", currency: String = "RUB", kind: String = "EXPENSE",
        hash: String? = null, category: String? = "cat",
    ) = TxnBackup(id, kind, "2026-01-01", 0, 0, account, 100, currency, null, null, null, category, null, null, hash)

    private fun payload(
        currencies: List<CurrencyBackup> = listOf(currency()),
        accounts: List<AccountBackup> = listOf(account()),
        categories: List<CategoryBackup> = listOf(category()),
        transactions: List<TxnBackup> = listOf(txn()),
        aliases: List<ImportAliasBackup> = emptyList(),
        recurrences: List<RecurrenceBackup> = emptyList(),
    ) = BackupPayload(
        schemaVersion = 7, exportedAt = 0, currencies = currencies, accounts = accounts, categories = categories,
        transactions = transactions, importBatches = emptyList(), importAliases = aliases, appSettings = emptyList(),
        recurrences = recurrences,
    )

    private fun problems(p: BackupPayload) = BackupRepository.validate(p)

    private inline fun <reified T : BackupProblem> List<BackupProblem>.only(): T {
        assertEquals(toString(), 1, size)
        assertTrue(toString(), single() is T)
        return single() as T
    }

    @Test fun `a consistent payload has no problems`() = assertEquals(emptyList<BackupProblem>(), problems(payload()))

    @Test fun `currency code must be three capital latin letters everywhere`() {
        // справочник, счёт, операция — по одному нарушению формата
        val p = problems(payload(currencies = listOf(currency("rub")), accounts = listOf(account(currency = "rub")), transactions = listOf(txn(currency = "rub"))))
        assertTrue(p.toString(), p.count { it is BackupProblem.InvalidValue && it.field == BackupField.CURRENCY_CODE } >= 3)
    }

    @Test fun `minorUnits must be 0 to 4 and displayScale within minorUnits`() {
        val a = problems(payload(currencies = listOf(currency(minor = 5, scale = 2)))).only<BackupProblem.InvalidValue>()
        assertEquals(BackupField.MINOR_UNITS, a.field)
        val b = problems(payload(currencies = listOf(currency(minor = 2, scale = 3)))).only<BackupProblem.InvalidValue>()
        assertEquals(BackupField.DISPLAY_SCALE, b.field)
    }

    @Test fun `unknown enum names are reported`() {
        assertEquals(BackupField.ACCOUNT_KIND, problems(payload(accounts = listOf(account(kind = "WALLET")))).only<BackupProblem.InvalidValue>().field)
        assertEquals(BackupField.KIND, problems(payload(categories = listOf(category(kind = "BOTH")))).only<BackupProblem.InvalidValue>().field)
        assertEquals(BackupField.ORIGIN, problems(payload(categories = listOf(category(origin = "ROBOT")))).only<BackupProblem.InvalidValue>().field)
        assertEquals(
            BackupField.ALIAS_KIND,
            problems(payload(aliases = listOf(ImportAliasBackup("MONEFY", "TAG", "x", "y")))).only<BackupProblem.InvalidValue>().field,
        )
    }

    @Test fun `duplicate ids, category name-kind and import hash are reported`() {
        assertEquals(BackupEntity.ACCOUNT, problems(payload(accounts = listOf(account(), account()))).only<BackupProblem.DuplicateKey>().entity)
        assertEquals(BackupEntity.TRANSACTION, problems(payload(transactions = listOf(txn("t1"), txn("t1")))).only<BackupProblem.DuplicateKey>().entity)
        val sameName = problems(payload(categories = listOf(category("c1", name = "Еда"), category("c2", name = "Еда")), transactions = emptyList()))
        assertEquals(BackupEntity.CATEGORY, sameName.only<BackupProblem.DuplicateKey>().entity)
        val sameHash = problems(payload(transactions = listOf(txn("t1", hash = "h"), txn("t2", hash = "h"))))
        assertEquals("h", sameHash.only<BackupProblem.DuplicateKey>().ref)
    }

    @Test fun `a transaction currency must equal its account currency (I-15)`() {
        val p = problems(payload(currencies = listOf(currency(), currency("USD")), transactions = listOf(txn(currency = "USD"))))
        assertEquals(BackupProblem.CurrencyMismatch(BackupEntity.TRANSACTION, "t1", "USD", "RUB"), p.only<BackupProblem.CurrencyMismatch>())
    }

    @Test fun `a transfer checks both sides and rejects transfer to itself`() {
        val transfer = txn("tr", kind = "TRANSFER", category = null).copy(toAccountId = "acc", toAmountMinor = 100, toCurrencyCode = "RUB")
        assertEquals(TransferIssue.SAME_ACCOUNT, problems(payload(transactions = listOf(transfer))).only<BackupProblem.BadTransfer>().issue)
        val other = payload(
            accounts = listOf(account(), account("acc2", currency = "USD")),
            currencies = listOf(currency(), currency("USD")),
            transactions = listOf(transfer.copy(toAccountId = "acc2", toCurrencyCode = "RUB")),
        )
        assertEquals(BackupProblem.CurrencyMismatch(BackupEntity.TRANSACTION, "tr", "RUB", "USD"), problems(other).only<BackupProblem.CurrencyMismatch>())
    }

    @Test fun `a category has at most one nesting level and is not its own parent`() {
        val chain = listOf(category("a"), category("b", parent = "a"), category("c", parent = "b"))
        assertEquals("c", problems(payload(categories = chain, transactions = emptyList())).only<BackupProblem.NestedCategory>().ref)
        assertEquals("a", problems(payload(categories = listOf(category("a", parent = "a")), transactions = emptyList())).only<BackupProblem.NestedCategory>().ref)
    }

    @Test fun `a recurrence currency must equal its account currency`() {
        val r = RecurrenceBackup("r", "EXPENSE", "acc", null, 100, "USD", null, "EVERY_N_DAYS", null, 7, "2026-09-01", null)
        val p = problems(payload(currencies = listOf(currency(), currency("USD")), recurrences = listOf(r)))
        assertEquals(BackupProblem.CurrencyMismatch(BackupEntity.RECURRENCE, "r", "USD", "RUB"), p.only<BackupProblem.CurrencyMismatch>())
    }
}
