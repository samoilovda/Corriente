import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// Corriente для компьютера (docs/SYNC_DESIGN.md §8): обычное окно на Compose Desktop. Данные — :core:data-jvm (тот же
// код, что на телефоне), обмен с телефоном — через общую папку Syncthing. Сетевого кода нет (I-24).

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Таблица линейных иконок категорий — тот же файл, что в приложении (:app), без копии в репозитории: копируется в build
// при сборке. Ключи иконок общие для телефона и компьютера.
val sharedIconVectors by tasks.registering(Sync::class) {
    from("$rootDir/app/src/main/kotlin/com/corriente/app/ui/common/CategoryIconVectors.kt")
    into(layout.buildDirectory.dir("generated/shared/kotlin/com/corriente/app/ui/common"))
}

sourceSets {
    main {
        kotlin.srcDir(layout.buildDirectory.dir("generated/shared/kotlin"))
    }
}

tasks.named("compileKotlin") { dependsOn(sharedIconVectors) }

dependencies {
    implementation(project(":core:money"))
    implementation(project(":core:data-jvm"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "com.corriente.desktop.MainKt"
        // Встроенный SQLite грузит нативную библиотеку — в новых Java это нужно разрешить явно.
        jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")
        nativeDistributions {
            // .dmg — macOS (собирается на Mac), .msi — Windows, .deb — Linux (каждый — на своей системе).
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Corriente"
            packageVersion = "1.0.0"
            description = "Corriente — учёт личных финансов"
            vendor = "Corriente"
            macOS { bundleID = "com.corriente.desktop" }
        }
    }
}
