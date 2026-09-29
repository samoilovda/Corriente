package corriente.buildsupport

/**
 * A2.12 (I-24): проверка **итогового** (merged) манифеста, а не только исходных — библиотека-зависимость
 * может незаметно добавить `INTERNET` или любое другое разрешение, и [InvariantGuards.scanManifest]
 * (он читает лишь манифесты модулей) этого не увидит.
 *
 * Разрешения приложения — закрытый список [allowedPermissions] (все приходят транзитивно: WorkManager, biometric,
 * androidx.core); всё вне списка и в особенности `INTERNET` валит сборку. Список синхронизирован с PRIVACY.md.
 */
object MergedManifestGuard {

    /** Разрешения итогового манифеста и зачем каждое (текст — для PRIVACY.md/README). */
    val allowedPermissions: Map<String, String> = linkedMapOf(
        "android.permission.WAKE_LOCK" to "WorkManager: не дать устройству заснуть во время фонового бэкапа/пересчёта",
        "android.permission.RECEIVE_BOOT_COMPLETED" to "WorkManager: восстановить расписание фоновых задач после перезагрузки",
        "android.permission.FOREGROUND_SERVICE" to "WorkManager: долгие фоновые задачи (экспорт бэкапа)",
        "android.permission.USE_BIOMETRIC" to "блокировка приложения отпечатком/лицом/PIN устройства",
        "android.permission.USE_FINGERPRINT" to "то же для старых версий Android (тянет библиотека biometric)",
    )

    private const val DYNAMIC_RECEIVER_SUFFIX = ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

    private val usesPermission = Regex("""<uses-permission(?:-sdk-\d+)?\b[^>]*>""", RegexOption.DOT_MATCHES_ALL)
    private val nameAttr = Regex("""android:name\s*=\s*"([^"]+)"""")

    /** Имена запрошенных разрешений итогового манифеста (комментарии не учитываются). */
    fun permissions(source: String): List<String> {
        val withoutComments = source.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
        return usesPermission.findAll(withoutComments)
            .mapNotNull { nameAttr.find(it.value)?.groupValues?.get(1) }
            .toList()
    }

    /** @return нарушения; пустой список — итоговый манифест чист. */
    fun scan(source: String, applicationId: String): List<String> = permissions(source).mapNotNull { name ->
        when {
            name == "android.permission.INTERNET" -> "разрешение INTERNET в итоговом манифесте запрещено (I-24, нет сети)"
            name in allowedPermissions -> null
            name == applicationId + DYNAMIC_RECEIVER_SUFFIX -> null // внутреннее разрешение androidx.core
            else -> "неизвестное разрешение $name в итоговом манифесте — добавить в allowlist MergedManifestGuard и PRIVACY.md или убрать"
        }
    }
}
