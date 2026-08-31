plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Android-модуль: Room, DAO, миграции, CSV-импорт, бэкап (ARCHITECTURE.md §5.1).
// НЕ содержит и не должен содержать сетевого кода (ADR-013, инвариант I-24).
android {
    namespace = "com.corriente.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

ksp {
    // exportSchema=true (ARCHITECTURE.md ADR-008, I-20): схемы коммитятся в репозиторий,
    // на их основе пишутся тесты миграций.
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:money"))

    // api, не implementation: :app создаёт БД через Room.databaseBuilder и держит
    // ссылку на AppDatabase (подтип RoomDatabase) в ручном DI-контейнере (ADR-011),
    // поэтому типы Room — часть публичного API модуля.
    api(libs.room.runtime)
    api(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlin.csv)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // MigrationTestHelper (см. AppDatabaseMigrationTest) - инструментальный тест, требует
    // подключённого устройства/эмулятора, поэтому androidTest, а не test.
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
