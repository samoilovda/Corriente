package corriente.buildsupport

/**
 * Статический скан исходников на запрещённые docs/BUILD_PLAN.md §1.5 конструкции.
 *
 * Живёт в buildSrc как чистый объект (не Detekt-правило, не JUnit-тест внутри модуля) сознательно:
 *  - Detekt тянул бы плагин статики, который заметно отстаёт от версии Kotlin (здесь Kotlin 2.3.x,
 *    стабильный Detekt ещё на 2.0.x) — ради пяти регэкспов это не окупается;
 *  - скан читает файлы с диска и работает даже для модулей, которые в конкретном окружении
 *    не может сконфигурировать Gradle (нет доступа к Google Maven — см. README);
 *  - логика вынесена сюда и покрыта unit-тестом (buildSrc/src/test) — правило плана
 *    «задача не считается сделанной без теста» соблюдено.
 *
 * Это осознанная замена «./gradlew detekt» из плана (§0 правило 7); формулировка правила
 * в BUILD_PLAN.md §1.5 / §0 обновлена соответственно.
 */
object InvariantGuards {

    /**
     * Пути (от корня репозитория, разделитель `/`), где `Float`/`Double` допустимы.
     * I-1 / ARCHITECTURE.md §2.1: деньги приходят уже посчитанными (Long минорных единиц);
     * `Float`/`Double` там — только на границе с системным API, которому Float нужен буквально
     * (координаты и угол сектора при отрисовке на Canvas; R2.3 — `LinearProgressIndicator`
     * принимает прогресс только как `Float`, целые проценты считает `ReportViewModel`).
     */
    val floatAllowlist: Set<String> = setOf(
        "app/src/main/kotlin/com/corriente/app/ui/report/ReportCharts.kt",
        "app/src/main/kotlin/com/corriente/app/ui/report/ReportScreen.kt",
    )

    private data class Rule(val regex: Regex, val reason: String)

    /** Правила для `.kt` во всех модулях. */
    private val globalKotlinRules = listOf(
        Rule(Regex("""NumberFormat\s*\.\s*getInstance"""), "NumberFormat.getInstance — locale-зависимый разбор денег запрещён (I-25)"),
        Rule(Regex("""\bDecimalFormat\s*\("""), "DecimalFormat — locale-зависимое форматирование денег запрещено (I-25)"),
        Rule(Regex("""fallbackToDestructiveMigration"""), "fallbackToDestructiveMigration запрещён в любом виде (I-20)"),
    )

    /** Денежные модули (:core:money, :core:data): `Float`/`Double` не нужны вовсе, включая приведения. */
    private val moneyModuleRules = listOf(
        Rule(Regex("""\bDouble\b"""), "тип Double запрещён в денежном коде (I-1)"),
        Rule(Regex("""\bFloat\b"""), "тип Float запрещён в денежном коде (I-1)"),
        Rule(Regex("""\.to(?:Double|Float)\s*\("""), "приведение к Double/Float в денежном коде запрещено (I-1)"),
    )

    /** :app / :widget: тип денег тот же (Long), но Canvas-код из [floatAllowlist] имеет право на Float. */
    private val uiTypeRules = listOf(
        Rule(Regex("""\bDouble\b"""), "тип Double запрещён в денежном коде (I-1)"),
        Rule(Regex("""\bFloat\b"""), "тип Float запрещён в денежном коде (I-1)"),
    )

    /**
     * I-3 / F1.1: арифметика над суммами обязана идти через [com.corriente.money.Money]
     * (там `Math.*Exact`). Ссылка на `Long::plus`/`Long::minus`/`Long::times` в файле, где
     * фигурируют минорные единицы, — обход этой проверки. Узкое правило вместо широкого
     * «любая арифметика над amountMinor вне Money», чтобы не ловить счётчики и индексы.
     */
    private val moneyArithmeticRules = listOf(
        Rule(
            Regex("""\bLong::(plus|minus|times|div)\b"""),
            "Long::plus/minus/times как ссылка на функцию рядом с суммами — арифметика денег в обход Money/Math.*Exact (I-3)",
        ),
    )

    private val moneyContentMarker = Regex("""\b(Minor|amountMinor|toAmountMinor|amount\.raw|amount\.amount\.raw)\b""")

    private fun stripKotlinComments(source: String): String = source
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""//.*"""), "")

    /**
     * @param relativePath путь файла от корня репозитория, `/` как разделитель
     * @param source полный текст файла
     * @return нарушения в формате `путь:строка: причина\n    <строка>`, пустой список — если чисто
     */
    fun scanKotlin(relativePath: String, source: String): List<String> {
        val inMoneyModule = relativePath.startsWith("core/money/") || relativePath.startsWith("core/data/")
        val strippedForContent = stripKotlinComments(source)
        val rules = buildList {
            addAll(globalKotlinRules)
            when {
                inMoneyModule -> addAll(moneyModuleRules)
                relativePath !in floatAllowlist -> addAll(uiTypeRules)
            }
            // core/money — единственное место, где сумма и есть Long, там правило неуместно.
            if (!relativePath.startsWith("core/money/") && moneyContentMarker.containsMatchIn(strippedForContent)) {
                addAll(moneyArithmeticRules)
            }
        }
        val violations = mutableListOf<String>()
        strippedForContent.lineSequence().forEachIndexed { index, line ->
            rules.forEach { rule ->
                if (rule.regex.containsMatchIn(line)) {
                    violations += "$relativePath:${index + 1}: ${rule.reason}\n    ${line.trim()}"
                }
            }
        }
        return violations
    }

    /** I-24: в приложении нет сети — ни одного `uses-permission` в манифесте. */
    fun scanManifest(relativePath: String, source: String): List<String> {
        val withoutComments = source.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
        val violations = mutableListOf<String>()
        withoutComments.lineSequence().forEachIndexed { index, line ->
            if (line.contains("uses-permission")) {
                violations += "$relativePath:${index + 1}: uses-permission запрещён (I-24, нет сети)\n    ${line.trim()}"
            }
        }
        return violations
    }
}
