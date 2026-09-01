plugins {
    kotlin("jvm") version embeddedKotlinVersion
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}

// Основная сборка потребляет классы buildSrc через `jar`. `finalizedBy` (не `dependsOn` —
// тот даёт цикл через compileTestKotlin) гоняет unit-тесты InvariantGuards на каждом
// вызове ./gradlew: тест упал → сборка упала.
tasks.named("jar") {
    finalizedBy("test")
}
