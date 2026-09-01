# Corriente

Личный офлайн-трекер финансов для Android (замена Monefy): мультивалютный учёт
без конвертации между валютами (каждая валюта — свой контур, ADR-012), виджет
на домашний экран, импорт истории из Monefy, локальные бэкапы. Один пользователь,
без серверной части и без сети вообще (ADR-013 — в приложении нет ни одной сетевой
библиотеки и разрешения `INTERNET`).

Статус: проектирование завершено, реализация начата (этап 1, T1.1). Все модули
(`:core:money`, `:core:data`, `:app`) собираются и тестируются.

* [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — архитектурный документ: разбор открытых
  аналогов, ADR по модели денег и валют, схема данных, виджет, слои, риски и этапы работ.
* [`docs/BUILD_PLAN.md`](docs/BUILD_PLAN.md) — пошаговый план разработки: этапы, задачи,
  критерии приёмки, правила для исполнителя.
* [`docs/INVARIANTS.md`](docs/INVARIANTS.md) — короткий чек-лист инвариантов для ревью кода.
* [`docs/MONEFY_IMPORT.md`](docs/MONEFY_IMPORT.md) — спецификация формата экспорта Monefy,
  проверенная на реальном файле, и алгоритм импорта.
* [`testdata/`](testdata) — синтетический экспорт Monefy для приёмочных тестов парсера
  (личных данных не содержит).

## Сборка

Требуется JDK 17+, Android SDK (platform 37, build-tools 36), доступ к Google Maven.
Путь к SDK — в `local.properties` (`sdk.dir=...`, файл не коммитится) или переменной
`ANDROID_HOME`.

```
./gradlew check assembleDebug     # юнит-тесты всех модулей, lint, verifyInvariantGuards, debug APK
```

`verifyInvariantGuards` (корневой `build.gradle.kts` поверх `InvariantGuards` из `buildSrc`) —
файловый скан **всего репозитория** на запрещённые конструкции (деньги через `Double`/`Float`,
locale-зависимый разбор, `fallbackToDestructiveMigration`, `uses-permission` в манифестах).
Сделан скан-задачей, а не Detekt-правилом, специально: читает `.kt` и `AndroidManifest.xml`
с диска, поэтому не зависит от того, конфигурируется ли модуль; плагин Detekt к тому же
отстаёт от версии Kotlin. Логика скана покрыта `InvariantGuardsTest` (buildSrc гоняет его
на каждой сборке). Подключён к `check`.

### Инструментальные тесты

`AppDatabaseMigrationTest` (`core/data/src/androidTest`) требует подключённого
эмулятора или устройства (API 26+):

```
./gradlew connectedCheck
```

Образец AVD: `system-images;android-36;google_apis;arm64-v8a` (или любой API 26+).
Плагин `androidx.room` сам кладёт экспортированные схемы в assets этих тестов.

### Совместимость версий

AGP 9 использует встроенный Kotlin (built-in Kotlin) — плагин `org.jetbrains.kotlin.android`
в Android-модулях не применяется, KGP приходит транзитивно с AGP. `:core:money` —
чистый Kotlin/JVM (`org.jetbrains.kotlin.jvm`), не зависит от Android SDK
(ARCHITECTURE.md §5.1). `:widget` появится на этапе 4 (BUILD_PLAN.md §6).

### Схема БД (`:core:data`)

Плагин `androidx.room` с `room { schemaDirectory(...) }` (ADR-008, I-20) пишет схему
в `core/data/schemas/` при каждой сборке — файлы коммитятся. Они дают
`MigrationTestHelper` эталон, с которым сверяется схема при каждой следующей миграции.
