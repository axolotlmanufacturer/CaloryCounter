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
  No hardcoded strings in Composables. Lint treats `HardcodedText` as an error.
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
- The system Gradle in the container (8.14.3) is too old for AGP 9.x. Always use `./gradlew`.
