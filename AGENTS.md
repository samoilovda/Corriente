# AGENTS.md

Corriente: personal offline multi-currency finance tracker for Android (Monefy replacement), plus a Compose Desktop window. No network code, no currency conversion.
Docs, code comments, commit messages and CHANGELOG are in Russian: write them in Russian.

## Modules
- `app/` Android app: Compose UI, ViewModels, navigation, workers, backup/lock screens.
- `core/money/` pure Kotlin/JVM money types (`Money`, calculator). No Android SDK.
- `core/data/` Room DB, repositories, backup, sync engine. Room schemas in `core/data/schemas/`.
- `core/data-jvm/` same sources as `core/data` (`srcDir ../data/src/main/kotlin`) built for desktop. Android-only files are excluded in its `androidOnly` list: keep Android SDK code in those files only (`AppDatabaseAndroid.kt`, `PreMigrationBackup.kt`, `PreMigrationSnapshotFactory.kt`, `widget/Widget*Store.kt`).
- `desktop/` Compose Desktop window over `core/data-jvm`.
- `widget/` Glance home-screen widgets.
- `buildSrc/` `InvariantGuards` (source scan) and `MergedManifestGuard`, with tests.
- `scripts/deploy.sh` debug build + install over Wi-Fi adb.
- `docs/` plans, audits, architecture. `testdata/` synthetic Monefy CSV. `.github/workflows/ci.yml` runs `check assembleDebug`.

## Commands
Needs JDK 17+ per README (CI uses 21), Android SDK (platform 37, build-tools 36) via `local.properties` `sdk.dir` or `ANDROID_HOME`.
- `./gradlew check assembleDebug` — unit tests of all modules, lint, `verifyInvariantGuards`, `verify<Variant>MergedManifest`, debug APK.
- `./gradlew connectedCheck` — instrumented tests; needs a running emulator/device, API 26+. Project AVD: `corriente` (API 36). Start: `$ANDROID_HOME/emulator/emulator -avd corriente -no-window -no-audio -gpu swiftshader_indirect -no-snapshot`. `adb` is not on PATH: use `$ANDROID_HOME/platform-tools/adb`. Known failures: see `docs/BACKLOG.md`. `connectedCheck` uninstalls the app (and its data) on every connected device: when a phone is plugged in, run it with `ANDROID_SERIAL=emulator-5554`.
- `./gradlew :desktop:packageDmg` — macOS installer, `desktop/build/compose/binaries/main/dmg/Corriente-<ver>.dmg`. `.msi` and `.deb` must be built on Windows/Linux (not run: no such machine).
- `./gradlew :desktop:run` — run desktop from sources (not run: GUI). `CORRIENTE_DATA_DIR=/path` points it at other data.
- `./gradlew assembleRelease` — signed minified APK (not run: needs a keystore). Fails by design without `RELEASE_STORE_FILE`/`RELEASE_STORE_PASSWORD`/`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD` (in `local.properties`, or `CORRIENTE_`-prefixed env vars). No fallback to debug signing.
- `scripts/deploy.sh` — build the `daily` variant (release-like: R8, not debuggable, but signed with the local debug key, so it updates a debug install keeping data) and install it on the connected phone; `ANDROID_SERIAL=<serial>` picks the device. Verified over USB.
- `adb shell input text` cannot type Cyrillic: use Latin in emulator smoke tests.

## Working rules
- One task = one commit. Message starts with the task ID, then a Russian summary: `V20: ввод с виджета ...`. Several IDs: `V3/V6/V9–V14: ...`. Check `git log` before writing one.
- Task IDs map to plan docs in `docs/` (`BUILD_PLAN.md`, `FIX_PLAN.md`, `ROADMAP.md`, `AUDIT_*.md`) and `docs/audit/<date>/`. New audits go to `docs/audit/<date>/AUDIT.md`.
- Before any code change read `docs/INVARIANTS.md` (I-1…I-27). If an invariant blocks the task, stop and ask; never work around it or leave a TODO.
- Every task needs a test, except purely visual Composables (move their logic into a ViewModel/pure function and test that).
- Before committing: `./gradlew check` green. Never commit red tests. For tasks touching DB, operations or widget also run `connectedCheck`, and say in the report if it fails.
- Do not widen scope. Out-of-scope findings go to `docs/BACKLOG.md`.
- New dependencies only from `docs/BUILD_PLAN.md` §1.3 and with explicit approval. Never add networking libraries, Play Services, Firebase, or any proprietary SDK.
- No `<uses-permission>` in source manifests (only `tools:node="remove"`); merged manifest must not gain `INTERNET`.
- No clever abstractions (no Arrow, generic repositories, custom DSLs, reflection, codegen besides Room and kotlinx-serialization).
- User-visible strings go to `app/src/main/res/values` (Russian, default) and `values-en`. Number/date formatting stays locale-independent (I-25).
- User-visible change → add an entry under `[Unreleased]` in `CHANGELOG.md`. Release version: `appVersionCode`/`appVersionName` in `gradle/libs.versions.toml` (code +1 per release APK, never reused); desktop uses separate `desktopVersion`. Rules: README "Версии".

## DB schema changes
- Current version: `AppDatabase.SCHEMA_VERSION` (`core/data/.../db/AppDatabase.kt`), 9 now. 
- Bump the version, add an explicit `Migration` in `AppDatabaseAndroid.kt` and append it to `ALL_MIGRATIONS`. Add the same migration on `SQLiteConnection` to `DESKTOP_MIGRATIONS` (`core/data-jvm/.../desktop/DesktopMigrations.kt`): the desktop does not compile `AppDatabaseAndroid.kt`, and without it an existing desktop database fails to open (`DesktopMigrationTest` checks the chain). `fallbackToDestructiveMigration` is forbidden (I-20, scanned).
- Commit the generated `core/data/schemas/com.corriente.data.db.AppDatabase/<N>.json`.
- Add a migration test in `AppDatabaseMigrationTest` (androidTest), then run `connectedCheck`.
- A new row kind/field that older apps would misread: bump the version even if the migration is empty (precedent: 8→9 for `ADJUSTMENT`); old phone/desktop builds then refuse newer backups and sync files. Phone and desktop must be updated together.
- A migration that transforms data (not just adds structure) must also be mirrored in `BackupRepository.restorePayload` or restore must run the migration chain (I-21).
- Check sync impact against `docs/SYNC_DESIGN.md` (I-22, I-26, I-27).
- Device-local settings (`auto_backup.*`, `app_lock.*`, `recurrence.last_error.*`) must not enter backups.

## Gotchas
- `verifyInvariantGuards` scans all `src/main` and `src/test` `.kt` plus manifests: no `Double`/`Float` money, no locale-dependent parsing, no raw `Long::plus/minus/times` on amounts (use `Money`).
- `buildSrc` packages must not contain a path segment named `build` (`.gitignore` swallows it).
- Keep `fragment` pinned at 1.8.6 in `libs.versions.toml`: older fragment (pulled by biometric) crashes SAF pickers on `MainActivity : FragmentActivity`.
- AGP 9 uses built-in Kotlin: do not apply `org.jetbrains.kotlin.android`. Glance is 1.2.0 for AGP 9 compatibility.
- Widget: the downward offset for HyperOS lives in `widget/.../QuickAddWidget.kt` and applies only on Xiaomi/Redmi/POCO. Keep that device check. The "−/+" widget deep link carries `from=widget`; after saving, the app closes back to the launcher.
- DAOs are interfaces/abstract classes: write fakes by hand in tests.
- Sync (Syncthing shared folder) and desktop details: `docs/SYNC_DESIGN.md`. Desktop has no Monefy import, auto-backup, lock or widget.

## Never touch or commit
- `local.properties`, keystores (`*.keystore`, `*.jks`), `RELEASE_*` values, `scripts/.device`.
- Build outputs and caches: `build/`, `.gradle/`, `.kotlin/`, `.idea/`, `.vscode/`, `*.iml`, `.DS_Store`.
- Personal data: `testdata/` must stay synthetic (no real backups or Monefy exports).

## Read next
- Architecture, ADRs, data model: `docs/ARCHITECTURE.md`. Invariants: `docs/INVARIANTS.md`.
- Executor rules and dependency policy: `docs/BUILD_PLAN.md` §0, §1.3, §1.5.
- Roadmap, deferred and rejected items: `docs/ROADMAP.md`. Open items: `docs/BACKLOG.md`.
- Sync and desktop: `docs/SYNC_DESIGN.md`. Monefy format (historical, import removed): `docs/MONEFY_IMPORT.md`.
- Release, signing, backup, build details: `README.md`. History: `CHANGELOG.md`.
