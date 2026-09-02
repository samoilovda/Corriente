import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// R6.1 (ROADMAP.md §8): ключ и пароли релизной подписи никогда не попадают в репозиторий.
// Источник — `local.properties` (гитигнорится, машинно-локальный файл) с фиксированными
// именами свойств, а если его нет (CI, чужая машина) — переменные окружения того же
// смысла. Оба способа задокументированы в README.md, раздел «Релизная сборка».
val keystoreProperties = Properties().apply {
    val local = rootProject.file("local.properties")
    if (local.exists()) {
        local.inputStream().use { load(it) }
    }
}

fun releaseSigningProperty(propertyKey: String, envKey: String): String? =
    keystoreProperties.getProperty(propertyKey)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }

val releaseStoreFilePath = releaseSigningProperty("RELEASE_STORE_FILE", "CORRIENTE_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningProperty("RELEASE_STORE_PASSWORD", "CORRIENTE_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningProperty("RELEASE_KEY_ALIAS", "CORRIENTE_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningProperty("RELEASE_KEY_PASSWORD", "CORRIENTE_RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig =
    releaseStoreFilePath != null && releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

// Не валимся здесь: этот файл конфигурируется для КАЖДОЙ Gradle-задачи, включая
// `testDebugUnitTest`/`assembleDebug`, у которых нет и не должно быть релизного ключа.
// Требование "ключ обязателен" применяется точечно — только когда в графе задач
// действительно есть релизная сборка :app (см. `gradle.taskGraph.whenReady` ниже).
gradle.taskGraph.whenReady {
    val needsReleaseSigning = allTasks.any { task ->
        task.project.path == project.path && task.name.endsWith("Release") &&
            (task.name.startsWith("assemble") || task.name.startsWith("bundle") || task.name.startsWith("package"))
    }
    if (needsReleaseSigning && !hasReleaseSigningConfig) {
        throw GradleException(
            "Релизная подпись не настроена. Заполните в local.properties (файл не коммитится): " +
                "RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD — " +
                "либо задайте переменные окружения CORRIENTE_RELEASE_STORE_FILE, " +
                "CORRIENTE_RELEASE_STORE_PASSWORD, CORRIENTE_RELEASE_KEY_ALIAS, CORRIENTE_RELEASE_KEY_PASSWORD. " +
                "Подробности — README.md, раздел «Релизная сборка». Отката на debug-подпись нет намеренно.",
        )
    }
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
        // R6.2 (ROADMAP.md §8): дисциплина версий — versionCode/versionName живут в
        // gradle/libs.versions.toml, а не здесь, чтобы вся числовая конфигурация проекта
        // читалась в одном месте. Правило: versionCode растёт на 1 с каждым релизным APK
        // и не переиспользуется; versionName — семантический (README.md, раздел «Версии»).
        versionCode = libs.versions.appVersionCode.get().toInt()
        versionName = libs.versions.appVersionName.get()
        // R1.3: SafBackupFolderInstrumentedTest — перечисление/чтение файлов из SAF-дерева
        // живёт в :app (SafBackupFolder тоже здесь), поэтому нужен androidTest раннер.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // R6.1 (ROADMAP.md §8): первая настоящая релизная сборка — раньше `release`
            // не был объявлен вовсе, и `assembleRelease` собирал неподписанный,
            // немнинифицированный APK по умолчанию AGP.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Иначе signingConfig не назначается вовсе (не debug!) — APK останется
            // неподписанным, и это специально: `gradle.taskGraph.whenReady` выше уже не
            // даст дойти до сборки в этом случае, а если бы дал — установка неподписанного
            // релизного APK честно падает, а не молча ставится debug-ключом.
        }
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
    // R5.2 (ROADMAP.md §9.2) — блокировка приложения по биометрии/PIN устройства.
    implementation(libs.androidx.biometric)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // R1.3: SafBackupFolderInstrumentedTest требует подключённого устройства/эмулятора.
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // R5.1: инструментальные UI-тесты (ROADMAP.md §9.1) — androidTest/debug, компилируются
    // и работают на настоящих Activity через ручной DI-контейнер (ADR-011), но требуют
    // эмулятора/устройства; в релизный APK не попадают.
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
