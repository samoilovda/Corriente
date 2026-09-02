package com.corriente.data.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.corriente.data.db.entity.AccountEntity
import com.corriente.data.db.entity.AccountKind
import com.corriente.data.db.entity.CategoryEntity
import com.corriente.data.db.entity.CategoryKind
import com.corriente.data.db.entity.CurrencyEntity
import com.corriente.data.db.entity.TxnEntity
import com.corriente.data.db.entity.TxnKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R2.1 `[БД]`: поиск находит операцию по заметке (FTS MATCH), по названию счёта и по названию
 * категории — расширение области поиска, которого не было в `matchesFilter`.
 */
class TxnDaoSearchInstrumentedTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            db.currencyDao().insertAll(listOf(CurrencyEntity("RUB", 2, 2, "₽", isActive = true)))
            db.accountDao().insert(AccountEntity("cash", "Наличные", "RUB", AccountKind.CASH, 0, 0))
            db.accountDao().insert(AccountEntity("card", "Тинькофф", "RUB", AccountKind.CARD, 0, 0))
            db.categoryDao().insert(CategoryEntity("food", "Еда", CategoryKind.EXPENSE, null, 0))
            db.categoryDao().insert(CategoryEntity("transport", "Такси", CategoryKind.EXPENSE, null, 0))
            db.txnDao().insertAll(
                listOf(
                    TxnEntity(
                        "byNote", TxnKind.EXPENSE, "2026-01-01", 0, 0, "cash", 100_00, "RUB",
                        categoryId = "food", note = "кофе с молоком",
                    ),
                    TxnEntity(
                        "byAccount", TxnKind.EXPENSE, "2026-01-02", 0, 0, "card", 200_00, "RUB",
                        categoryId = "transport",
                    ),
                    TxnEntity(
                        "byCategory", TxnKind.EXPENSE, "2026-01-03", 0, 0, "cash", 300_00, "RUB",
                        categoryId = "transport",
                    ),
                    TxnEntity(
                        "unrelated", TxnKind.EXPENSE, "2026-01-04", 0, 0, "cash", 400_00, "RUB",
                        categoryId = "food", note = "ничего общего",
                    ),
                ),
            )
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun findsByNoteAccountNameAndCategoryName() = runBlocking {
        val byNote = db.txnDao().search(buildFtsPrefixQuery("кофе"), buildLikePattern("кофе")).first()
        assertEquals(listOf("byNote"), byNote.map { it.id })

        val byAccount = db.txnDao().search(buildFtsPrefixQuery("тинькофф"), buildLikePattern("тинькофф")).first()
        assertEquals(listOf("byAccount"), byAccount.map { it.id })

        val byCategory = db.txnDao().search(buildFtsPrefixQuery("такси"), buildLikePattern("такси")).first()
        assertEquals(setOf("byAccount", "byCategory"), byCategory.map { it.id }.toSet())

        val noMatch = db.txnDao().search(buildFtsPrefixQuery("зпрлдг"), buildLikePattern("зпрлдг")).first()
        assertTrue(noMatch.isEmpty())
    }

    // R2.1: поиск синхронизирован через триггеры, а не отдельный insert — правка заметки сразу
    // видна в поиске, старое значение больше не находится.
    @Test
    fun updatingNoteResyncsFtsIndex() = runBlocking {
        val original = db.txnDao().getById("byNote")!!
        db.txnDao().update(original.copy(note = "новый текст"))

        assertTrue(db.txnDao().search(buildFtsPrefixQuery("кофе"), buildLikePattern("кофе")).first().isEmpty())
        assertEquals(
            listOf("byNote"),
            db.txnDao().search(buildFtsPrefixQuery("новый"), buildLikePattern("новый")).first().map { it.id },
        )
    }
}
