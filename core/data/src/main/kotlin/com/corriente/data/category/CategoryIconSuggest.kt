package com.corriente.data.category

/**
 * Иконка категории хранится строкой в `category.icon` (схема не меняется): либо эмодзи как есть, либо ссылка на
 * линейную иконку из каталога приложения — `mi:<ключ>` (каталог ключей — `CategoryIconCatalog` в `:app`).
 */
const val MATERIAL_ICON_PREFIX = "mi:"

fun materialIconRef(key: String): String = MATERIAL_ICON_PREFIX + key

/** Ключ линейной иконки из [icon], либо null, если это эмодзи/пусто. */
fun materialIconKey(icon: String?): String? = icon?.takeIf { it.startsWith(MATERIAL_ICON_PREFIX) }?.removePrefix(MATERIAL_ICON_PREFIX)

/**
 * Правила подбора иконки по названию: первое совпадение побеждает, поэтому частное — раньше общего («такси» раньше
 * «транспорта», «кафе» раньше «еды», «питомцы» раньше «дома»). Как сравниваются слова — см. [matches].
 */
val CATEGORY_ICON_RULES: List<Pair<List<String>, String>> = listOf(
    listOf("такси", "taxi", "uber", "яндекс го") to "local_taxi",
    listOf("бензин", "топлив", "заправ", "fuel", "gas station", "petrol") to "local_gas_station",
    listOf("парков", "parking") to "local_parking",
    listOf("самол", "авиа", "flight", "airline") to "flight",
    listOf("поезд", "ржд", "train", "railway") to "train",
    listOf("метро", "subway", "metro") to "subway",
    listOf("путешеств", "отпуск", "travel", "vacation", "trip", "отел", "hotel") to "luggage",
    listOf("ремонт авто", "автосервис", "car repair", "шиномонтаж") to "car_repair",
    listOf("машин", "авто", "car", "vehicle") to "directions_car",
    listOf("транспорт", "проезд", "автобус", "transport", "bus", "commute") to "directions_bus",
    listOf("кафе", "кофе", "cafe", "coffee") to "local_cafe",
    listOf("ресторан", "restaurant", "dining", "обед", "lunch") to "restaurant",
    listOf("фастфуд", "fast food", "fastfood", "доставка еды") to "fastfood",
    listOf("парикмах", "стриж", "барбер", "hair", "barber") to "content_cut",
    listOf("алкогол", "пиво", "вино", "бар", "alcohol", "beer", "wine", "bar") to "local_bar",
    listOf("продукт", "супермаркет", "grocer", "supermarket", "еда", "food", "питани") to "local_grocery_store",
    listOf("коммунал", "жкх", "utilit", "электр", "свет", "electric") to "lightbulb",
    listOf("вода", "water") to "water_drop",
    listOf("интернет", "internet", "wifi", "wi-fi") to "wifi",
    listOf("связь", "телефон", "мобил", "сотов", "phone", "mobile") to "phone_android",
    listOf("ремонт", "стройк", "repair", "renovation") to "build",
    listOf("быт", "хозтовар", "хозяйств", "household", "уборк", "cleaning") to "cleaning_services",
    listOf("мебел", "furniture") to "chair",
    listOf("питом", "животн", "собак", "кошк", "pet", "dog", "cat", "puppy") to "pets",
    listOf("аренд", "квартир", "жиль", "ипотек", "дом", "home", "housing", "rent", "mortgage") to "home",
    listOf("аптек", "лекарств", "pharm", "medicine", "drug") to "medication",
    listOf("здоров", "медиц", "врач", "больниц", "клиник", "стомат", "health", "doctor", "medical", "dental") to "local_hospital",
    listOf("одежд", "обув", "cloth", "shoe", "apparel") to "checkroom",
    listOf("техник", "электрон", "гаджет", "electronic", "gadget") to "devices",
    listOf("игр", "game", "gaming") to "sports_esports",
    listOf("кино", "театр", "концерт", "movie", "cinema", "theater", "concert") to "movie",
    listOf("развлеч", "досуг", "entertain", "leisure", "fun") to "celebration",
    listOf("спорт", "фитнес", "трениров", "sport", "fitness", "gym") to "fitness_center",
    listOf("книг", "book") to "menu_book",
    listOf("образов", "учёб", "учеб", "курс", "школ", "educat", "school", "course", "study") to "school",
    listOf("подар", "gift", "present") to "card_giftcard",
    listOf("дет", "ребён", "ребен", "kid", "child", "baby") to "child_care",
    listOf("красот", "уход", "косметик", "beauty", "cosmetic", "salon", "spa") to "spa",
    listOf("подписк", "subscript", "стриминг", "streaming") to "subscriptions",
    listOf("страхов", "insur") to "shield",
    listOf("кредит", "долг", "заём", "заем", "loan", "credit", "debt") to "account_balance",
    listOf("налог", "комисс", "штраф", "пошлин", "tax", "taxes", "fee", "fine", "commission") to "receipt_long",
    listOf("благотвор", "пожертв", "донат", "charity", "donation") to "volunteer_activism",
    listOf("сигарет", "табак", "smok", "tobacco") to "smoking_rooms",
    listOf("зарплат", "оклад", "salary", "wage", "payroll") to "payments",
    listOf("преми", "бонус", "bonus", "award") to "emoji_events",
    listOf("подработ", "фриланс", "side", "freelance", "работ", "work") to "work",
    listOf("процент", "вклад", "депозит", "interest", "deposit", "savings", "накоплен") to "savings",
    listOf("дивиденд", "инвест", "акци", "dividend", "invest", "stock") to "trending_up",
    listOf("кэшбэк", "кешбэк", "возврат", "cashback", "refund") to "currency_exchange",
    listOf("продаж", "sale", "sell") to "sell",
    listOf("перевод", "transfer") to "swap_horiz",
    listOf("прочее", "разное", "другое", "other", "misc") to "more_horiz",
)

/**
 * Иконка для категории без своей иконки — по названию; null, если ни одно правило не подошло (тогда экран
 * рисует первую букву названия).
 */
fun suggestCategoryIcon(name: String): String? {
    val tokens = name.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return null
    return CATEGORY_ICON_RULES.firstOrNull { (keywords, _) -> keywords.any { matches(it.trim(), tokens) } }
        ?.second?.let(::materialIconRef)
}

/**
 * Сравнение по словам, а не по подстроке, — иначе «car» находится в «care», «bar» в «barber».
 * Фраза из нескольких слов — подряд идущие слова; русская основа — начало слова («продукт» → «Продукты»);
 * английское слово — целиком или с окончанием -s, длинное (от 5 букв) — и как начало слова («grocer» → «groceries»).
 */
private fun matches(keyword: String, tokens: List<String>): Boolean {
    if (' ' in keyword) return tokens.joinToString(" ").contains(keyword)
    val latin = keyword.all { it in 'a'..'z' || it == '-' }
    return tokens.any { token ->
        if (!latin) token.startsWith(keyword)
        else token == keyword || token == keyword + "s" || (keyword.length >= 5 && token.startsWith(keyword))
    }
}
