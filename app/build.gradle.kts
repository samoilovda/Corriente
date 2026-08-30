plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// :app - Compose UI, ViewModel, навигация, ручной DI-контейнер (ARCHITECTURE.md §5.1,
// BUILD_PLAN.md §1.4 и ADR-011: без Hilt - для ~15 зависимостей ручной контейнер проще
// и не даёт непрозрачных ошибок KSP-кодогенерации).
android {
    namespace = "com.corriente.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.corriente.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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

    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // -extended, не -core: нужны AccountBalanceWallet/PieChart для нижней навигации,
    // их нет в куцем наборе -core (см. итоговое сообщение сессии - решение задокументировано).
    implementation("androidx.compose.material:material-icons-extended")

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
