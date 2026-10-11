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
 * Иконка категории: линейная (`mi:<ключ>`) окрашивается [color]; эмодзи рисуется как есть; без иконки — первая
 * буква названия. Неизвестный ключ (бэкап из более новой версии) — тоже первая буква, а не пустое место.
 */
@Composable
fun CategoryGlyph(icon: String?, name: String, color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    val key = materialIconKey(icon)
    val vector = key?.let(CategoryIconCatalog::vector)
    Box(modifier.size(size + 4.dp), contentAlignment = Alignment.Center) {
        when {
            vector != null -> Icon(vector, contentDescription = null, tint = color, modifier = Modifier.size(size))
            key == null && !icon.isNullOrBlank() -> Text(icon, style = TextStyle(fontSize = (size.value * 0.9f).sp))
            else -> Text(
                name.trim().take(1).uppercase(),
                style = TextStyle(fontSize = (size.value * 0.8f).sp),
                color = color,
            )
        }
    }
}
