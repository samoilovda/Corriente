plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    base
}

/**
 * Защитные проверки из docs/BUILD_PLAN.md §1.5 / §0 (правило 7): статический скан исходников
 * на запрещённые конструкции. Сделан как обычная файловая задача, а не Detekt-правило и не
 * JUnit-тест внутри модуля, специально: она сканирует ВЕСЬ репозиторий по файловой системе,
 * включая Android-модули (:core:data, :app), которые в этой среде разработки не могут быть
 * даже сконфигурированы Gradle'ом (нет доступа к Google Maven — см. README раздел
 * "Известное ограничение окружения"). Так правило действует независимо от того, собирается
 * ли модуль в конкретном окружении.
 */
val verifyInvariantGuards by tasks.registering {
    group = "verification"
    description = "Проверяет отсутствие запрещённых конструкций (docs/BUILD_PLAN.md §1.5)"

    val kotlinSources = fileTree(rootDir) {
        include("**/src/main/**/*.kt", "**/src/test/**/*.kt")
        exclude("**/build/**")
    }
    val manifests = fileTree(rootDir) {
        include("**/AndroidManifest.xml")
        exclude("**/build/**")
    }

    inputs.files(kotlinSources)
    inputs.files(manifests)

    doLast {
        val violations = mutableListOf<String>()

        // I-1 / I-25: деньги — не Double/Float; денежные строки — не через NumberFormat/DecimalFormat.
        val forbiddenTokenPatterns = mapOf(
            Regex("""\bDouble\b""") to "тип Double запрещён в денежном коде (I-1)",
            Regex("""\bFloat\b""") to "тип Float запрещён в денежном коде (I-1)",
            Regex("""NumberFormat\s*\.\s*getInstance""") to "NumberFormat.getInstance — locale-зависимый разбор денег запрещён (I-25)",
            Regex("""\bDecimalFormat\s*\(""") to "DecimalFormat — locale-зависимое форматирование денег запрещено (I-25)",
            Regex("""fallbackToDestructiveMigration""") to "fallbackToDestructiveMigration запрещён в любом виде (I-20)",
        )
        // Модули, где допустим Float (координаты Compose Canvas и т.п. — ARCHITECTURE.md §5.3):
        // деньги там уже посчитаны и переданы как готовые числа, но сам модуль не денежный.
        val floatAllowedPathFragments = listOf("/ui/chart/", "/ui/canvas/")

        kotlinSources.forEach { file ->
            val relativePath = file.relativeTo(rootDir).path
            val withoutComments = file.readText()
                .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
                .replace(Regex("""//.*"""), "")
            withoutComments.lineSequence().forEachIndexed { index, line ->
                forbiddenTokenPatterns.forEach { (pattern, reason) ->
                    val isFloatException = pattern.pattern.contains("Float") &&
                        floatAllowedPathFragments.any { relativePath.contains(it) }
                    if (!isFloatException && pattern.containsMatchIn(line)) {
                        violations += "$relativePath:${index + 1}: $reason\n    ${line.trim()}"
                    }
                }
            }
        }

        // I-24: в приложении нет сети — ни одного uses-permission в манифесте.
        manifests.forEach { file ->
            val relativePath = file.relativeTo(rootDir).path
            file.readText().lineSequence().forEachIndexed { index, line ->
                if (line.contains("uses-permission")) {
                    violations += "$relativePath:${index + 1}: uses-permission запрещён (I-24, нет сети)\n    ${line.trim()}"
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Найдены запрещённые конструкции (docs/BUILD_PLAN.md §1.5):\n\n" +
                    violations.joinToString("\n\n")
            )
        }
        logger.lifecycle("verifyInvariantGuards: OK, просканировано ${kotlinSources.files.size} .kt и ${manifests.files.size} AndroidManifest.xml")
    }
}

tasks.named("check") {
    dependsOn(verifyInvariantGuards)
}
