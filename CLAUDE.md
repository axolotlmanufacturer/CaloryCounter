# CLAUDE.md – Kalorienzähler

Offline calorie counter & deficit calculator for Android with a German UI.
The full specification is in `PROMPT.md` (source of truth). This file holds the
working knowledge: commands, architecture, conventions, decisions and gotchas.
Keep it current whenever one of those changes.

## Project settings (PROMPT.md §0, confirmed)

| Setting | Value |
|---|---|
| App name | `Kalorienzähler` (`@string/app_name`) |
| applicationId / Kotlin package | `de.privat.kalorienzaehler` |
| Brand color (fallback seed) | `#2E7D5B` |
| Distribution | sideloaded APK, private use only |
| minSdk | 26 (Android 8.0) |

## Workflow

- Work in the phases of PROMPT.md §12. After each phase: `./gradlew lint test`
  green, a conventional-commit commit, a short report. Then **wait for "weiter"**.
- Branches: `main` is the base. Phase work goes on the working branch, with a PR into `main`.
- Small, reviewable commits (`feat:`, `fix:`, `chore:`, `test:`, `docs:`, `build:`).
- When the spec is ambiguous or reality conflicts with it, stop and ask. Don't deviate silently.

## Commands

The Android build exists from Phase 1 and the data pipeline from Phase 4.

```bash
# Android
./gradlew lint test                    # quality gate, every phase
./gradlew assembleDebug                # debug APK (uses the sample food DB)
./gradlew assembleRelease -PfoodDb=full   # release APK (needs the full food DB + keystore.properties)
./gradlew connectedDebugAndroidTest    # instrumented tests; needs a device (none in the cloud container)
./gradlew :domain:test                 # pure-Kotlin calculators only

# Food database pipeline (tools/food-db-builder)
cd tools/food-db-builder
uv venv && uv pip install -r requirements.txt
.venv/bin/python build_food_db.py --sample 3000   # small dev DB
.venv/bin/python build_food_db.py                 # full DB (release)
.venv/bin/pytest

# Cloud container: install the Android SDK etc. (needs dl.google.com)
bash scripts/cloud-setup.sh

# Verify there is no INTERNET permission in an APK
$ANDROID_HOME/build-tools/<ver>/aapt2 dump permissions app/build/outputs/apk/release/app-release.apk
```

## Architecture

- `:domain`: pure Kotlin/JVM, no Android imports. Holds calculators (BMR, TDEE, goal/safety
  rules, macros, weight trend, projections, adaptive TDEE), models, text normalization,
  barcode normalization and German number formatting/parsing.
- `:app`: single activity, Jetpack Compose + Material 3, Navigation Compose, Hilt.
  - `data/fooddb/`: **read-only** Room DB `foods.db`, created from an asset with FTS4 search.
  - `data/userdb/`: **read-write** Room DB `user.db` (diary, weight, custom foods, favorites).
    Exported schemas are in `app/schemas/` (committed). Use explicit migrations; no
    destructive fallback in release.
  - `data/prefs/`: DataStore (profile and settings).
  - `data/repository/`: repositories. Keep them export-friendly; CSV/JSON export is the likely next feature.
  - `ui/`: one package per screen.
- `tools/food-db-builder/`: Python + DuckDB pipeline that builds `foods.db` from BLS 4.0 and
  Open Food Facts. It creates tables using Room's exported `createSql`.

## Conventions

- **All user-facing text lives in `res/values/strings.xml`** (German is the default locale).
  No hardcoded strings in Composables. Lint's `HardcodedText` only covers XML, so
  `NoHardcodedUiStringsTest` scans the Kotlin sources for literals passed to `Text(…)`,
  `contentDescription = …`, `title = …` and similar.
- Icons: Material Symbols vector drawables in `res/drawable/ic_*_24.xml` (outlined; `*_filled_*`
  for selected states), downloaded from `google/material-design-icons` (`symbols/android/…`)
  with the `android:tint="?attr/colorControlNormal"` line removed (that attr needs AppCompat).
  Do not add `material-icons-extended` (frozen, slow builds).
- German formatting everywhere: `1.234 kcal`, `72,4 kg`, `13.10.2026`. Weeks start on Monday.
  Metric only. Number inputs accept both `,` and `.`.
- Dates: `java.time` only. Day boundaries use the device time zone. Inject a `Clock` for testability.
- Energy is computed in `Double`. kcal is displayed as a whole number. Macros are whole grams
  in summaries and one decimal in detail views. kJ = kcal × 4.184.
- **Never invent nutrition values.** Missing values are shown as "–". Estimated portion weights
  are flagged `estimated = true`.
- Safety rules (PROMPT.md §6.4): explain in friendly, neutral German and never block silently.
  "Over target" is shown in neutral/amber, never alarm-red.
- No main-thread DB access (StrictMode is on in debug). Use keyed lazy lists.
- Liquids: 1 ml is treated as 1 g; the UI shows "ml".

## Decisions

- Native Kotlin + Compose (PROMPT.md §3). Gradle Kotlin DSL, version catalog, KSP (no kapt).
- Toolchain: AGP 9.4.1 with **built-in Kotlin** (never apply `org.jetbrains.kotlin.android`),
  Kotlin 2.4.21 pinned via the root `plugins { … apply false }` block, Gradle 9.7.1 (9.8.x
  prints deprecation warnings with AGP 9.4.1; KGP 2.4.21 is tested up to Gradle 9.7),
  compileSdk/targetSdk 37 (required by Compose 1.12 / Lifecycle 2.11), minSdk 26, Java 17
  bytecode. Compose BOM 2026.09.00 (Compose 1.12.1, Material3 1.4.0).
- AGP 9 defaults to keep in mind: unit tests exist only for the debug build type
  (`./gradlew test` = `testDebugUnitTest` + JVM modules); app `R` fields are non-final
  (no `R.*` in `when` branches); `proguard-rules.pro` must exist; BuildConfig is enabled
  explicitly in `:app`.
- Compose UI tests use the v2 APIs (`androidx.compose.ui.test.junit4.v2.createComposeRule`).
  Robolectric 4.17 runs them on SDK 37 with German qualifiers (`app/src/test/resources/robolectric.properties`);
  SDK 36/37 sandboxes need **JDK 21** for the test JVM, so never configure a toolchain below 21.
- `hiltViewModel()` comes from `androidx.hilt:hilt-lifecycle-viewmodel-compose` (not the deprecated
  `hilt-navigation-compose`). The Dagger processor is `com.google.dagger:hilt-compiler` on `ksp`.
- Backup rules: one `<exclude>` per domain (a `root` rule does not cover files/databases/shared_prefs);
  never more than two attributes per element in `res/xml/backup_rules.xml` (the API 26/27 parser aborts);
  `foods.db` is excluded from device transfer.
- Keep androidx.core's `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`: removing it makes
  `ContextCompat.registerReceiver(…, RECEIVER_NOT_EXPORTED)` throw on API 26–32. The manifest check allows it.
- Theme fallback colors (`ui/theme/Color.kt`) are generated from `#2E7D5B` with Material Color
  Utilities (tonal spot, spec 2021) — regenerate, don't hand-edit.
- Offline by design: `INTERNET` is removed via `tools:node="remove"`. No analytics, Firebase or
  Play Services. The only runtime permission is `CAMERA`, requested in context.
- Backup: cloud backup excludes everything; device-to-device transfer is allowed (API 31+
  `dataExtractionRules`; `fullBackupContent` with `requireFlags` on API 28–30; everything
  excluded on API 26–27).
- **Open Food Facts source:** the Parquet export (Hugging Face) is not reachable from the
  cloud environment. The pipeline uses OFF's official S3 exports instead:
  `en.openfoodfacts.org.products.csv.gz` for nutrients (tab-separated, read with DuckDB,
  needed columns only), joined on `code` with `openfoodfacts-mobile-dump-products.tsv.gz`
  for German names. The CSV has no `product_name_de` column. Approved by the user.
- **BLS 4.0** (free, CC BY 4.0, no registration): `BLS_4_0_Daten_2025_DE.xlsx` +
  `BLS_4_0_Components_DE_EN.xlsx` from blsdb.de/download.
- No emulator in the cloud container. Instrumented tests are committed and run on the user's
  device. The same checks also run as Robolectric tests inside `./gradlew test`.
- The keystore can't live in the ephemeral cloud container. How to handle it is decided in Phase 8.

## Gotchas

- **Cloud environment network policy:** these hosts must be in the environment's Allowed
  domains: `dl.google.com` (Android SDK **and** Google Maven, since `maven.google.com`
  redirects there), `downloads.gradle.org` (Gradle wrapper, since `services.gradle.org`
  redirects there) and `blsdb.de` (BLS). Maven Central, the Gradle Plugin Portal, PyPI and the
  OFF S3 bucket (`openfoodfacts-ds.s3.eu-west-3.amazonaws.com`) are reachable.
- The cloud container is ephemeral. The Android SDK, venv and raw data must be reinstalled or
  re-downloaded per session. Push work before ending a session.
- No `/dev/kvm`, so no emulator or Gradle Managed Devices in the cloud container.
- SDK package ids for new platforms carry a minor version: `platforms;android-37.0` (not `android-37`).
- Robolectric downloads `android-all-instrumented:17-robolectric-15733970-i7` (~240 MB) from Maven
  Central on the first test run without retrying; `scripts/cloud-setup.sh` prefetches it into `~/.m2`.
- The system Gradle in the container (8.14.3) is too old for AGP 9.x. Always use `./gradlew`.
- `repo.maven.apache.org` answers **429** through the container proxy; `repo1.maven.org` works.
  `scripts/cloud-setup.sh` installs `~/.gradle/init.d/maven-central-repo1.init.gradle.kts`
  (copy in `scripts/gradle/`) so Gradle prefers repo1. Not needed on a normal machine.
- The committed `gradle-wrapper.jar` is the official 8.14.x wrapper jar (generated offline);
  it downloads the pinned 9.7.1 distribution (SHA-256 checked). Once `downloads.gradle.org` is
  reachable, refresh it with `./gradlew wrapper --gradle-version 9.7.1
  --gradle-distribution-sha256-sum acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a`.
- `:domain` can be tested without the Android SDK: see "Testing `:domain` without the SDK".

## Testing `:domain` without the SDK

When Google Maven / the Gradle distribution are unreachable, the pure-Kotlin module can still be
built with the system Gradle from a throwaway settings file outside the repo, e.g.
`settings.gradle.kts` with `include(":domain"); project(":domain").projectDir = file("<repo>/domain")`
plus a `gradle/libs.versions.toml` containing only `kotlin`, `junit`, `kotlin-test` and the
`kotlin-jvm` plugin, then
`gradle -I <repo>/scripts/gradle/maven-central-repo1.init.gradle.kts :domain:test`.
