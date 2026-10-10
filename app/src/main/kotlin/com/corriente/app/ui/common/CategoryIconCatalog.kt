package com.corriente.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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

    private val groups: List<Pair<Int, List<Pair<String, ImageVector>>>> = listOf(
        R.string.icon_section_food to listOf(
            "local_grocery_store" to Icons.Outlined.LocalGroceryStore,
            "shopping_basket" to Icons.Outlined.ShoppingBasket,
            "restaurant" to Icons.Outlined.Restaurant,
            "local_cafe" to Icons.Outlined.LocalCafe,
            "fastfood" to Icons.Outlined.Fastfood,
            "local_pizza" to Icons.Outlined.LocalPizza,
            "bakery_dining" to Icons.Outlined.BakeryDining,
            "lunch_dining" to Icons.Outlined.LunchDining,
            "icecream" to Icons.Outlined.Icecream,
            "local_bar" to Icons.Outlined.LocalBar,
            "wine_bar" to Icons.Outlined.WineBar,
        ),
        R.string.icon_section_transport to listOf(
            "local_taxi" to Icons.Outlined.LocalTaxi,
            "directions_car" to Icons.Outlined.DirectionsCar,
            "directions_bus" to Icons.Outlined.DirectionsBus,
            "subway" to Icons.Outlined.Subway,
            "train" to Icons.Outlined.Train,
            "tram" to Icons.Outlined.Tram,
            "flight" to Icons.Outlined.Flight,
            "local_gas_station" to Icons.Outlined.LocalGasStation,
            "local_parking" to Icons.Outlined.LocalParking,
            "car_repair" to Icons.Outlined.CarRepair,
            "two_wheeler" to Icons.Outlined.TwoWheeler,
            "directions_bike" to Icons.Outlined.DirectionsBike,
            "luggage" to Icons.Outlined.Luggage,
        ),
        R.string.icon_section_home to listOf(
            "home" to Icons.Outlined.Home,
            "apartment" to Icons.Outlined.Apartment,
            "lightbulb" to Icons.Outlined.Lightbulb,
            "water_drop" to Icons.Outlined.WaterDrop,
            "local_fire_department" to Icons.Outlined.LocalFireDepartment,
            "chair" to Icons.Outlined.Chair,
            "bed" to Icons.Outlined.Bed,
            "kitchen" to Icons.Outlined.Kitchen,
            "local_laundry_service" to Icons.Outlined.LocalLaundryService,
            "cleaning_services" to Icons.Outlined.CleaningServices,
            "build" to Icons.Outlined.Build,
            "format_paint" to Icons.Outlined.FormatPaint,
            "yard" to Icons.Outlined.Yard,
        ),
        R.string.icon_section_tech to listOf(
            "phone_android" to Icons.Outlined.PhoneAndroid,
            "call" to Icons.Outlined.Call,
            "wifi" to Icons.Outlined.Wifi,
            "computer" to Icons.Outlined.Computer,
            "devices" to Icons.Outlined.Devices,
            "tv" to Icons.Outlined.Tv,
            "headphones" to Icons.Outlined.Headphones,
            "sports_esports" to Icons.Outlined.SportsEsports,
            "subscriptions" to Icons.Outlined.Subscriptions,
        ),
        R.string.icon_section_health to listOf(
            "local_hospital" to Icons.Outlined.LocalHospital,
            "medication" to Icons.Outlined.Medication,
            "medical_services" to Icons.Outlined.MedicalServices,
            "monitor_heart" to Icons.Outlined.MonitorHeart,
            "spa" to Icons.Outlined.Spa,
            "content_cut" to Icons.Outlined.ContentCut,
            "fitness_center" to Icons.Outlined.FitnessCenter,
            "pool" to Icons.Outlined.Pool,
            "smoking_rooms" to Icons.Outlined.SmokingRooms,
        ),
        R.string.icon_section_leisure to listOf(
            "checkroom" to Icons.Outlined.Checkroom,
            "shopping_bag" to Icons.Outlined.ShoppingBag,
            "local_mall" to Icons.Outlined.LocalMall,
            "diamond" to Icons.Outlined.Diamond,
            "card_giftcard" to Icons.Outlined.CardGiftcard,
            "celebration" to Icons.Outlined.Celebration,
            "cake" to Icons.Outlined.Cake,
            "movie" to Icons.Outlined.Movie,
            "theater_comedy" to Icons.Outlined.TheaterComedy,
            "music_note" to Icons.Outlined.MusicNote,
            "sports_soccer" to Icons.Outlined.SportsSoccer,
            "beach_access" to Icons.Outlined.BeachAccess,
            "hiking" to Icons.Outlined.Hiking,
            "photo_camera" to Icons.Outlined.PhotoCamera,
        ),
        R.string.icon_section_family to listOf(
            "child_care" to Icons.Outlined.ChildCare,
            "child_friendly" to Icons.Outlined.ChildFriendly,
            "school" to Icons.Outlined.School,
            "menu_book" to Icons.Outlined.MenuBook,
            "pets" to Icons.Outlined.Pets,
            "volunteer_activism" to Icons.Outlined.VolunteerActivism,
        ),
        R.string.icon_section_money to listOf(
            "payments" to Icons.Outlined.Payments,
            "work" to Icons.Outlined.Work,
            "emoji_events" to Icons.Outlined.EmojiEvents,
            "savings" to Icons.Outlined.Savings,
            "trending_up" to Icons.Outlined.TrendingUp,
            "account_balance" to Icons.Outlined.AccountBalance,
            "credit_card" to Icons.Outlined.CreditCard,
            "receipt_long" to Icons.Outlined.ReceiptLong,
            "shield" to Icons.Outlined.Shield,
            "currency_exchange" to Icons.Outlined.CurrencyExchange,
            "sell" to Icons.Outlined.Sell,
            "swap_horiz" to Icons.Outlined.SwapHoriz,
            "attach_money" to Icons.Outlined.AttachMoney,
        ),
        R.string.icon_section_other to listOf(
            "more_horiz" to Icons.Outlined.MoreHoriz,
            "star" to Icons.Outlined.StarOutline,
            "label" to Icons.Outlined.Label,
            "local_shipping" to Icons.Outlined.LocalShipping,
            "local_post_office" to Icons.Outlined.LocalPostOffice,
        ),
    )

    private val vectors: Map<String, ImageVector> = groups.flatMap { it.second }.toMap()

    val sections: List<IconSection> = groups.map { (title, items) -> IconSection(title, items.map { it.first }) }

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
