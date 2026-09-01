plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

// :widget — виджет домашнего экрана на Glance (ARCHITECTURE.md §4, BUILD_PLAN.md §6).
// Рисуется в процессе лаунчера и читает ТОЛЬКО готовый WidgetSnapshot из DataStore
// (:core:data) — ни Room, ни расчётов здесь нет. Сетевого кода нет (I-24).
android {
    namespace = "com.corriente.widget"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
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

    // api: :app держит ссылку на CorrienteWidget (подтип GlanceAppWidget) и вызывает
    // updateAll() после записи в БД — тип Glance часть публичного API модуля.
    api(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.kotlinx.coroutines.android)
}
