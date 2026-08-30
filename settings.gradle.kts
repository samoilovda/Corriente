rootProject.name = "Corriente"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        // google() понадобится репозиториям android-модулей (см. ниже) - добавить там
        // локально/в CI, где dl.google.com доступен. Здесь не подключаем и в корневые
        // репозитории не пускаем, чтобы не ронять резолвинг :core:money в этой среде.
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// :core:money - чистый Kotlin/JVM, не зависит от Android SDK (ARCHITECTURE.md §5.1).
// Полностью собирается и тестируется без доступа к Google Maven - см. INVARIANTS.md.
include(":core:money")

// Android-модули (ARCHITECTURE.md §5.1, BUILD_PLAN.md §1.4) закомментированы:
// эта среда разработки не имеет доступа к Google Maven (dl.google.com заблокирован
// политикой egress), поэтому Android Gradle Plugin и androidx.* здесь не резолвятся,
// и любая попытка сконфигурировать эти модули уронит сборку целиком, включая
// уже готовый и протестированный :core:money.
// Раскомментировать в окружении с доступом к Google Maven (локальная машина, CI).
// Исходники модулей лежат на диске (core/data, app) - собрать их там, где есть сеть.
// include(":core:data")
// include(":app")
// include(":widget") // добавляется на этапе 4 (BUILD_PLAN.md §6)
