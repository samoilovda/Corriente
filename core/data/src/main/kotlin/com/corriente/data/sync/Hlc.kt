package com.corriente.data.sync

/**
 * Гибридные логические часы (docs/SYNC_DESIGN.md §2): версия строки = (время устройства в мс, счётчик, устройство).
 * В отличие от «просто времени», изменение, сделанное после того, как устройство увидело чужое, всегда старше
 * увиденного — даже если часы этого устройства отстают.
 *
 * Строковое представление [encode] сравнивается лексикографически так же, как сами часы: время — 13 цифр
 * (хватит до 2286 года), счётчик — 6 hex-цифр, затем id устройства. Им и пользуется LWW.
 */
data class Hlc(val wallMs: Long, val counter: Int, val deviceId: String) : Comparable<Hlc> {

    init {
        require(wallMs in 0..MAX_WALL) { "wallMs out of range: $wallMs" }
        require(counter in 0..MAX_COUNTER) { "counter out of range: $counter" }
        require(deviceId.isNotEmpty()) { "empty deviceId" } // id устройства — UUID, его дефисы не мешают: decode режет на 3 части
    }

    override fun compareTo(other: Hlc): Int =
        compareValuesBy(this, other, Hlc::wallMs, Hlc::counter, Hlc::deviceId)

    // Locale.ROOT: цифры всегда ASCII — иначе на арабской локали строки перестали бы сортироваться (I-25-стиль).
    fun encode(): String = String.format(java.util.Locale.ROOT, "%013d%s%06x%s%s", wallMs, SEPARATOR, counter, SEPARATOR, deviceId)

    /** Следующее значение для локального изменения: не меньше часов устройства и строго больше предыдущего. */
    fun tick(nowMs: Long): Hlc =
        if (nowMs > wallMs) Hlc(nowMs, 0, deviceId) else bump(wallMs)

    /** Учесть чужую версию [remote] (при применении изменений): дальнейшие локальные правки будут старше неё. */
    fun receive(remote: Hlc, nowMs: Long): Hlc {
        val wall = maxOf(nowMs, wallMs, remote.wallMs)
        return when {
            wall == wallMs && wall == remote.wallMs -> Hlc(wall, maxOf(counter, remote.counter) + 1, deviceId).checked()
            wall == wallMs -> bump(wall)
            wall == remote.wallMs -> Hlc(wall, remote.counter + 1, deviceId).checked()
            else -> Hlc(wall, 0, deviceId)
        }
    }

    private fun bump(wall: Long) = Hlc(wall, counter + 1, deviceId).checked()

    // Переполнение счётчика (миллион изменений в одну миллисекунду) — переходим на следующую миллисекунду.
    private fun Hlc.checked(): Hlc = if (counter > MAX_COUNTER) Hlc(wallMs + 1, 0, deviceId) else this

    companion object {
        private const val SEPARATOR = '-'
        private const val MAX_WALL = 9_999_999_999_999L
        private const val MAX_COUNTER = 0xFFFFFF

        fun zero(deviceId: String) = Hlc(0, 0, deviceId)

        fun decode(text: String): Hlc {
            val parts = text.split(SEPARATOR, limit = 3)
            require(parts.size == 3 && parts[0].length == 13 && parts[1].length == 6) { "bad HLC: $text" }
            return Hlc(parts[0].toLong(), parts[1].toInt(16), parts[2])
        }
    }
}
