package com.corriente.data.seed

import com.corriente.data.db.entity.CategoryKind

/**
 * Одна типовая категория «из коробки», до записи в БД.
 *
 * [id] детерминирован (`seed-…`): так его видно в отладке, а повторный прогон seeding остаётся
 * идемпотентным. `origin` у всех — `USER`: пользователь волен переименовать, архивировать или
 * удалить любую из них, как свою собственную. `display_order` при вставке — позиция в
 * [DEFAULT_CATEGORIES].
 */
data class SeedCategory(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val icon: String,
    val color: Int,
)

private fun exp(id: String, name: String, icon: String, color: Long) =
    SeedCategory("seed-exp-$id", name, CategoryKind.EXPENSE, icon, color.toInt())

private fun inc(id: String, name: String, icon: String, color: Long) =
    SeedCategory("seed-inc-$id", name, CategoryKind.INCOME, icon, color.toInt())

/**
 * Базовый набор категорий доходов и расходов, которым приложение наполняет пустой список при
 * первом запуске ([com.corriente.data.db.AppDatabase.seedCallback]) и миграцией v5→v6 для уже
 * установленных копий. В обоих случаях — ТОЛЬКО если таблица `category` пуста: не спорим с уже
 * заведёнными пользователем категориями и не ловим конфликт уникального индекса (name, kind).
 *
 * Названия русские — как и категории, создаваемые импортом из Monefy (MONEFY_IMPORT.md §5):
 * имя категории это данные в БД, а не строковый ресурс, локализовать его нечем.
 */
val DEFAULT_CATEGORIES: List<SeedCategory> = listOf(
    exp("groceries", "Продукты", "🛒", 0xFF66BB6A),
    exp("dining", "Кафе и рестораны", "🍽️", 0xFFFF7043),
    exp("transport", "Транспорт", "🚌", 0xFF42A5F5),
    exp("car", "Автомобиль", "🚗", 0xFF5C6BC0),
    exp("housing", "Жильё", "🏠", 0xFF8D6E63),
    exp("utilities", "Коммунальные услуги", "💡", 0xFFFFCA28),
    exp("connectivity", "Связь и интернет", "📶", 0xFF26C6DA),
    exp("health", "Здоровье", "💊", 0xFFEF5350),
    exp("clothing", "Одежда и обувь", "👕", 0xFFAB47BC),
    exp("entertainment", "Развлечения", "🎬", 0xFFEC407A),
    exp("sport", "Спорт", "🏋️", 0xFF9CCC65),
    exp("education", "Образование", "📚", 0xFF7E57C2),
    exp("gifts", "Подарки", "🎁", 0xFFD4E157),
    exp("travel", "Путешествия", "✈️", 0xFF29B6F6),
    exp("kids", "Дети", "🧸", 0xFFFFA726),
    exp("pets", "Питомцы", "🐾", 0xFFA1887F),
    exp("beauty", "Красота и уход", "💇", 0xFFF06292),
    exp("subscriptions", "Подписки", "💳", 0xFF7986CB),
    exp("fees", "Налоги и комиссии", "🧾", 0xFF78909C),
    exp("other-exp", "Прочее", "📦", 0xFF90A4AE),

    inc("salary", "Зарплата", "💰", 0xFF66BB6A),
    inc("bonus", "Премия", "🏆", 0xFFFFCA28),
    inc("sidework", "Подработка", "🧰", 0xFF42A5F5),
    inc("interest", "Проценты по вкладу", "🏦", 0xFF26A69A),
    inc("dividends", "Дивиденды", "📈", 0xFF9CCC65),
    inc("gift", "Подарок", "🎁", 0xFFD4E157),
    inc("refund", "Возврат средств", "↩️", 0xFF4DD0E1),
    inc("sale", "Продажа", "🏷️", 0xFFFF8A65),
    inc("other-inc", "Прочее", "📦", 0xFF90A4AE),
)
