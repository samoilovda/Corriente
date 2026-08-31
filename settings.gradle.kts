rootProject.name = "Corriente"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

// :core:money - чистый Kotlin/JVM, не зависит от Android SDK (ARCHITECTURE.md §5.1).
// Полностью собирается и тестируется без доступа к Google Maven - см. INVARIANTS.md.
include(":core:money")

// Android-модули (ARCHITECTURE.md §5.1, BUILD_PLAN.md §1.4). Требуют доступа к Google Maven
// (dl.google.com) для Android Gradle Plugin и androidx.*.
include(":core:data")
include(":app")
// include(":widget") // добавляется на этапе 4 (BUILD_PLAN.md §6)
