plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Десктопная (JVM) сборка того же слоя данных, что :core:data (docs/SYNC_DESIGN.md §8, D2): исходники не копируются —
// берутся из core/data/src/main, кроме файлов, завязанных на Android SDK (миграции/засев на SupportSQLiteDatabase,
// копии БД через android.database, хранилища виджета на Context). Room — та же версия, SQLite — встроенный драйвер.
// Денежные правила, репозитории, бэкап и синхронизация — один и тот же код на телефоне и на десктопе.
// Сетевого кода нет и здесь (I-24).

val androidOnly = listOf(
    "**/db/AppDatabaseAndroid.kt",
    "**/db/PreMigrationBackup.kt",
    "**/db/PreMigrationSnapshotFactory.kt",
    "**/widget/WidgetConfigStore.kt",
    "**/widget/WidgetSnapshotStore.kt",
)

sourceSets {
    main {
        kotlin.srcDir("../data/src/main/kotlin")
        kotlin.exclude(androidOnly)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

ksp {
    // Схемы в репозитории ведёт Android-модуль (core/data/schemas); здесь — только в build, чтобы не перезаписать.
    arg("room.schemaLocation", layout.buildDirectory.dir("schemas").get().asFile.path)
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core:money"))
    api(libs.room.runtime)
    api(libs.sqlite.bundled)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlin.csv)

    testImplementation(libs.junit)
}
