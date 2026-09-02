plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// :app - Compose UI, ViewModel, навигация, ручной DI-контейнер (ARCHITECTURE.md §5.1,
// BUILD_PLAN.md §1.4 и ADR-011: без Hilt - для ~15 зависимостей ручной контейнер проще
// и не даёт непрозрачных ошибок KSP-кодогенерации).
android {
    namespace = "com.corriente.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.corriente.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
        // R1.3: SafBackupFolderInstrumentedTest — перечисление/чтение файлов из SAF-дерева
        // живёт в :app (SafBackupFolder тоже здесь), поэтому нужен androidTest раннер.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Тест-данные Monefy лежат в testdata/ в корне репозитория (на них ссылаются docs);
    // приёмочный тест импорта читает их из classpath (см. Stage3AcceptanceTest).
    sourceSets {
        named("test") { resources.srcDir("$rootDir/testdata") }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:money"))
    implementation(project(":core:data"))
    implementation(project(":widget"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    // -extended, не -core: нужны AccountBalanceWallet/PieChart для нижней навигации,
    // их нет в куцем наборе -core (см. итоговое сообщение сессии - решение задокументировано).
    implementation(libs.compose.material.icons.extended)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    // с этапа 5, только автобэкап по расписанию (BUILD_PLAN §1.3).
    implementation(libs.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // R1.3: SafBackupFolderInstrumentedTest требует подключённого устройства/эмулятора.
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
