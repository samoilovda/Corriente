package com.corriente.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corriente.app.R
import com.corriente.data.category.materialIconKey

/** Раздел выбора иконок: подпись и ключи (`mi:<ключ>` в `category.icon`). */
data class IconSection(@StringRes val titleRes: Int, val keys: List<String>)

/**
 * Линейные иконки категорий в духе Monefy — контурные Material Icons (библиотека уже в сборке, сети не нужно).
 * Ключ — стабильная строка, которая хранится в БД и бэкапе; переименовывать ключи нельзя, только добавлять.
 */
object CategoryIconCatalog {

    private val sectionTitles: Map<String, Int> = mapOf(
        "food" to R.string.icon_section_food,
        "transport" to R.string.icon_section_transport,
        "home" to R.string.icon_section_home,
        "tech" to R.string.icon_section_tech,
        "health" to R.string.icon_section_health,
        "leisure" to R.string.icon_section_leisure,
        "family" to R.string.icon_section_family,
        "money" to R.string.icon_section_money,
        "other" to R.string.icon_section_other,
    )

    private val vectors: Map<String, ImageVector> = CategoryIconVectors.byKey

    val sections: List<IconSection> = CategoryIconVectors.groups.map { (id, items) -> IconSection(sectionTitles.getValue(id), items.map { it.first }) }

    val keys: Set<String> get() = vectors.keys

    fun vector(key: String): ImageVector? = vectors[key]

    /** M-1: подбор подходящей иконки для стандартных категорий, если иконка не была явно выбрана. */
    fun fallbackVectorForName(name: String): ImageVector? {
        val lower = name.trim().lowercase()
        return when {
            lower.contains("продукт") || lower.contains("grocer") -> vectors["local_grocery_store"]
            lower.contains("кафе") || lower.contains("кофе") || lower.contains("cafe") || lower.contains("coffee") -> vectors["local_cafe"]
            lower.contains("ресторан") || lower.contains("еда") || lower.contains("обед") || lower.contains("eating") || lower.contains("dining") || lower.contains("food") -> vectors["restaurant"]
            lower.contains("такси") || lower.contains("taxi") -> vectors["local_taxi"]
            lower.contains("авто") || lower.contains("машин") || lower.contains("car") -> vectors["directions_car"]
            lower.contains("транспорт") || lower.contains("автобус") || lower.contains("bus") -> vectors["directions_bus"]
            lower.contains("метро") || lower.contains("subway") -> vectors["subway"]
            lower.contains("поезд") || lower.contains("train") -> vectors["train"]
            lower.contains("дом") || lower.contains("квартир") || lower.contains("аренд") || lower.contains("home") || lower.contains("rent") -> vectors["home"]
            lower.contains("аптек") || lower.contains("здоров") || lower.contains("врач") || lower.contains("health") -> vectors["medication"]
            lower.contains("спорт") || lower.contains("фитнес") || lower.contains("sport") || lower.contains("gym") -> vectors["fitness_center"]
            lower.contains("связь") || lower.contains("телефон") || lower.contains("phone") -> vectors["phone_android"]
            lower.contains("интернет") || lower.contains("wifi") -> vectors["wifi"]
            lower.contains("зарплат") || lower.contains("salary") -> vectors["payments"]
            lower.contains("перевод") || lower.contains("transfer") -> vectors["swap_horiz"]
            lower.contains("подар") || lower.contains("gift") -> vectors["card_giftcard"]
            lower.contains("подписк") || lower.contains("subscription") -> vectors["subscriptions"]
            lower.contains("развлечен") || lower.contains("кино") || lower.contains("movie") -> vectors["movie"]
            lower.contains("одежд") || lower.contains("обувь") || lower.contains("cloth") -> vectors["checkroom"]
            lower.contains("питом") || lower.contains("животн") || lower.contains("pet") -> vectors["pets"]
            lower.contains("ремонт") || lower.contains("repair") -> vectors["build"]
            lower.contains("путешеств") || lower.contains("travel") -> vectors["flight"]
            else -> null
        }
    }
}

/** Эмодзи для вкладки «Эмодзи» выбора иконки; свой эмодзи можно вписать там же. */
val CATEGORY_EMOJI: List<String> = listOf(
    "🛒", "🍎", "🥖", "🍽️", "☕", "🍔", "🍕", "🍺", "🍷", "🍰",
    "🚕", "🚗", "🚌", "🚇", "🚆", "✈️", "⛽", "🅿️", "🚲", "🛵",
    "🏠", "🏢", "💡", "💧", "🔥", "🛋️", "🧹", "🔧", "🪴", "🧺",
    "📱", "📶", "💻", "📺", "🎧", "🎮", "💳", "🧾", "🏦", "💰",
    "💊", "🏥", "🦷", "💇", "💅", "🏋️", "🧘", "🚬", "👕", "👟",
    "🛍️", "💍", "🎁", "🎉", "🎬", "🎭", "🎵", "⚽", "🏖️", "📷",
    "🧸", "👶", "🎓", "📚", "🐾", "🐶", "🐱", "❤️", "🙏", "💼",
    "📈", "🏆", "🪙", "💵", "↩️", "🏷️", "🛡️", "📦", "✉️", "⭐",
)

/**
 * Иконка категории: линейная (`mi:<ключ>`) окрашивается [color]; эмодзи рисуется как есть; без иконки — автоподбор
 * иконки по имени либо аккуратный полужирный монограмм-бейдж.
 */
@Composable
fun CategoryGlyph(icon: String?, name: String, color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    val key = materialIconKey(icon)
    val vector = key?.let(CategoryIconCatalog::vector) ?: if (icon.isNullOrBlank()) CategoryIconCatalog.fallbackVectorForName(name) ?: CategoryIconCatalog.vector("sell") else null
    Box(modifier.size(size + 4.dp), contentAlignment = Alignment.Center) {
        when {
            vector != null -> Icon(vector, contentDescription = null, tint = color, modifier = Modifier.size(size))
            key == null && !icon.isNullOrBlank() -> Text(icon, style = TextStyle(fontSize = (size.value * 0.9f).sp))
            else -> Text(
                name.trim().take(1).uppercase().ifEmpty { "?" },
                style = TextStyle(
                    fontSize = (size.value * 0.75f).sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                ),
                color = color,
            )
        }
    }
}

