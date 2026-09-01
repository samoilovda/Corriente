import corriente.buildsupport.InvariantGuards

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    base
}

/**
 * Защитные проверки из docs/BUILD_PLAN.md §1.5 / §0 (правило 7): статический скан исходников
 * на запрещённые конструкции. Сделан файловой задачей поверх [InvariantGuards] (buildSrc),
 * а не Detekt-правилом, сознательно (обоснование — в KDoc [InvariantGuards]). Сама логика
 * скана покрыта unit-тестом `InvariantGuardsTest`, который buildSrc прогоняет на каждой сборке.
 *
 * Задача читает `.kt` и `AndroidManifest.xml` с диска и потому работает независимо от того,
 * может ли Gradle сконфигурировать Android-модули в конкретном окружении.
 */
val verifyInvariantGuards by tasks.registering {
    group = "verification"
    description = "Проверяет отсутствие запрещённых конструкций (docs/BUILD_PLAN.md §1.5)"

    val kotlinSources = fileTree(rootDir) {
        include("**/src/main/**/*.kt", "**/src/test/**/*.kt")
        exclude("**/build/**", "buildSrc/**") // buildSrc — сам скан и его тест-фикстуры
    }
    val manifests = fileTree(rootDir) {
        include("**/AndroidManifest.xml")
        exclude("**/build/**", "buildSrc/**")
    }
    val root = rootDir

    inputs.files(kotlinSources)
    inputs.files(manifests)

    doLast {
        val violations = mutableListOf<String>()

        kotlinSources.forEach { file ->
            val rel = file.relativeTo(root).path.replace(java.io.File.separatorChar, '/')
            violations += InvariantGuards.scanKotlin(rel, file.readText())
        }
        manifests.forEach { file ->
            val rel = file.relativeTo(root).path.replace(java.io.File.separatorChar, '/')
            violations += InvariantGuards.scanManifest(rel, file.readText())
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Найдены запрещённые конструкции (docs/BUILD_PLAN.md §1.5):\n\n" +
                    violations.joinToString("\n\n"),
            )
        }
        logger.lifecycle(
            "verifyInvariantGuards: OK, просканировано ${kotlinSources.files.size} .kt и ${manifests.files.size} AndroidManifest.xml",
        )
    }
}

tasks.named("check") {
    dependsOn(verifyInvariantGuards)
}
