plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Чистый Kotlin/JVM, без Android SDK (ARCHITECTURE.md §5.1: ":core:money не должен
// компилироваться с Android SDK — это проверяется тем, что он объявлен как kotlin("jvm")").
//
// Байткод-таргет 17 задаётся явно на компиляторе, без Gradle toolchain: toolchain
// потребовал бы установленный JDK 17 (или сеть для его автозагрузки), а собирающий эту
// сборку JDK 21 умеет эмитить байткод для более старого таргета сам по себе.

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
