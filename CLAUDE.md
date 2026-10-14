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
./gradlew assembleDebug                # debug APK (packages the sample food DB, 0.8 MB)
./gradlew assembleDebug -PfoodDb=full # debug APK with the full food DB (59 MB; out/foods-full.db must exist)
./gradlew assembleRelease -PfoodDb=full   # release APK (needs the full food DB + keystore.properties)
./gradlew connectedDebugAndroidTest    # instrumented tests; needs a device (none in the cloud container)
./gradlew :domain:test                 # pure-Kotlin calculators only
# search speed on the full DB (JVM sanity check, skipped for the sample DB):
./gradlew :app:testDebugUnitTest --tests '*FullDatabaseSearchSpeedTest' -PfoodDb=full -i

# Food database pipeline (tools/food-db-builder)
cd tools/food-db-builder
uv venv && uv pip install -r requirements.txt
.venv/bin/python build_food_db.py --sample 3000   # small dev DB -> out/foods-sample.db (committed)
.venv/bin/python build_food_db.py                 # full DB -> out/foods-full.db (gitignored; downloads ~1.4 GB into data/raw/)
.venv/bin/pytest                                  # 169 tests, no network

# Cloud container: install the Android SDK etc. (needs dl.google.com). Runs automatically via
# the SessionStart hook (.claude/hooks/session-start.sh) in Claude Code cloud sessions.
bash scripts/cloud-setup.sh

# Verify there is no INTERNET permission in an APK
# (app-release-unsigned.apk until the Phase 8 signingConfig exists)
$ANDROID_HOME/build-tools/<ver>/aapt2 dump permissions app/build/outputs/apk/release/app-release-unsigned.apk
```

## Architecture

- `:domain`: pure Kotlin/JVM, no Android imports. Holds calculators (BMR, TDEE, goal/safety
  rules, macros, weight trend, projections, adaptive TDEE), models, text normalization,
  barcode normalization and German number formatting/parsing.
- `:app`: single activity, Jetpack Compose + Material 3, **Navigation 3**, Hilt.
  - `data/fooddb/`: **read-only** Room DB `foods.db`, created from an asset with FTS4 search
    (`FoodEntity`, `FoodFtsEntity`, `FoodPortionEntity`, `FoodMetaEntity`, `FoodDao`, `FoodDbStatus`).
  - `data/userdb/`: **read-write** Room DB `user.db` (diary, weight, custom foods, favorites).
    Exported schemas are in `app/schemas/` (committed). Use explicit migrations; no
    destructive fallback in release. DAOs exist for weight, diary, custom foods and favorites.
  - `data/prefs/`: DataStore (profile and settings).
  - `data/repository/`: repositories. Keep them export-friendly; CSV/JSON export is the likely next feature.
    The screens never touch the food DB directly: `FoodCatalog` (`DefaultFoodCatalog`) merges own foods, recents,
    favorites and `FoodRepository` (`RoomFoodRepository`, the bundled DB) behind one search/load API.
  - `ui/`: one package per screen (`today`, `addfood` = all full-screen logging steps, `diary` = shared
    diary text/components, `profile` = form fields and plan text, `settings`, `onboarding`, `navigation`).
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
- **Navigation 3** instead of the spec's Navigation Compose (§3): Nav2 is in maintenance mode, Nav3
  1.2.0 is stable. Chosen by the user in Phase 2. `ui/navigation/`: `@Serializable` `NavKey` routes,
  `NavigationState` (one back stack per tab, survives process death; pattern from
  android/nav3-recipes "multiplestacks") and `Navigator`; `NavDisplay` takes the decorated entries.
  ViewModels are scoped per entry via `lifecycle-viewmodel-navigation3`'s decorator.
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
- No emulator in the cloud container. Instrumented tests (from Phase 4) are committed and run on
  the user's device. The same checks also run as Robolectric tests inside `./gradlew test`.
- Test JVMs are pinned to Java 21 (`javaLauncher` in `app/build.gradle.kts`) because Robolectric's
  SDK 37 sandbox needs it; bytecode stays Java 17.
- `:domain` compiles with `-Xjdk-release=17` so JDK 18+ APIs (e.g. `List.removeFirst`) cannot slip
  in: Android lint never analyzes that module. JDK ≤17 APIs missing on Android 8 (e.g.
  `Stream.toList`) are still possible there; prefer Kotlin stdlib APIs in `:domain`.
- **Form state (Phase 2):** `ProfileDraft` (in `:domain`) holds the text of every input field and
  `evaluate(today)` returns errors, the parsed `UserProfile`, the `GoalPlan` and the notices, so
  onboarding and Profil share one tested path. ViewModels keep the draft in Compose snapshot state
  (`mutableStateOf` + `derivedStateOf`), not in a `StateFlow`, because a flow round trip makes the
  cursor jump in text fields. Screens are a stateless `*Content` composable plus a thin wrapper that
  calls `hiltViewModel()`; tests drive the `*Content` directly.
- **Safety notices (PROMPT.md §6.4):** `GoalPlanner` never blocks silently: every intervention
  (goal not offered, rate capped to 1 % of body weight, calorie floor 1.200/1.500, goal weight below
  BMI 18,5, Katch-McArdle without body fat, macros that do not fit) yields a `GoalNotice`; the German
  wording lives in `ui/profile/Messages.kt`. Users under 18 get a maintenance estimate plus the
  `EstimateNotMadeForMinors` caveat; "Abnehmen" and "Zunehmen" are not offered to them.
  When 1,6 g/kg protein alone exceeds the target, protein is reduced to fit and the user is told
  (the spec was silent here).
- **Hilt in tests (Phase 2):** `testing/TestModules.kt` replaces `RepositoryModule` and `ClockModule`
  via `@TestInstallIn` with the in-memory fakes from `testing/Fakes.kt`; `hilt-android-testing` plus
  `kspTest(hilt.compiler)` and `HiltTestApplication`. `NavigationTest` and `MainActivityTest` use it.
- **200 % font scale is tested (PROMPT.md §8):** `ui/FontScaleTest` renders every onboarding step and
  Profil at `fontScale = 2.0` on 360×640 dp with `@GraphicsMode(NATIVE)` and fails on clipped or
  off-screen text via `testing/TextOverflow.kt` (`clippedTexts()`). Legacy graphics mode has fake
  text metrics, and `hasVisualOverflow` fires for every shrink-wrapped text, so neither is usable;
  the detector uses `didOverflowHeight`, `multiParagraph.didExceedMaxLines` and bounds off screen.
  `TextOverflowDetectorTest` proves the detector can fail. Use one `setContent` per test.
- **Diary model (Phase 3):** `:domain/diary` holds `DiaryEntry` (nutrient snapshot: kcal + nullable
  protein/carbs/fat), `CustomFood`, `NutrientsPer100.forGrams`, `DiaryMath` (day/meal totals, incomplete-macro
  count), `EnergyBalance`/`MacroProgress`, `FoodPlausibility` (warnings that never block), the text-based form
  drafts (`QuickEntryDraft`, `CustomFoodDraft`, `AmountInput`) and `AmountUnit`. `Meal`, `FoodSource`, `PerUnit` live
  there too (stored by name/code, so the Room schema is unchanged: `user.db` is still version 1).
  Repositories expose domain models, never entities (`RoomDiaryRepository`, `RoomCustomFoodRepository`).
- **Entries and units:** `diary_entry.unit` is what the UI shows: `g`, `ml` or a portion label such as
  `Portion (30 g)` (the label carries the size, so a food edited later cannot change old entries). A quick
  entry has `source = QUICK`, `amount = 1`, `grams = 0` ("unknown", it has no amount) and may leave macros null.
  Editing an amount rescales weight and nutrients proportionally from the snapshot (`DiaryEntry.withAmount`);
  per-100 values of an entry are derived (`per100()`), so editing works even if the food is gone.
  Energy inputs are always **kcal**, whatever the display unit.
- **Undo:** delete = `diary.delete(id)` + event; undo = `diary.restore(entry)` (re-insert with the same id, REPLACE).
  "Von gestern kopieren" = `copyMeal` in one Room transaction, returns the new ids so the copy can be undone.
  **Only an empty meal is filled** (the button shows only for empty meals, and the repository enforces it, so a double
  tap cannot duplicate the copy). The spec (7.2) does not say; the user may want it on non-empty meals too.
- **Heute:** `HorizontalPager` with 73.001 pages (one per day, +-100 years around today); the date bar
  (arrows, picker, "Heute") is a custom top bar. While a row is being swiped away the pager's user scroll is
  off (`rowSwiping`), otherwise a fast full-width swipe also changed the day. `SwipeToDismissBoxState` is
  created with `remember`, **not** `rememberSwipeToDismissBoxState` (that one is saved per list item). "Over target" uses
  `overTargetColor()` (amber, light/dark), never red. Swipe between days works on the cards and meal headers, not on an
  entry row (the row's swipe-to-delete takes the gesture); the arrows, the picker and the "Zu heute" icon always work.
  Screen readers get "Löschen" as a custom action on the focused row (`Surface(onClick)`), not on the swipe container.
- **Full-screen steps** (`AddFoodRoute`, `QuickEntryRoute`, `PortionRoute`, `CustomFoodRoute`, all `@Serializable`, days as
  epoch days) use `FullScreenScaffold` (own top bar without fixed height, content insets = navigation bar + IME)
  and `BottomActionBar`. ViewModels get their route through Hilt assisted injection:
  `hiltViewModel<VM, VM.Factory>(creationCallback = { it.create(route) })`. After saving, `Navigator.popToTopLevelRoot()`;
  a new custom food created from the add flow `replaceCurrent`s itself by the amount screen.
- **Search text:** `TextNormalizer` (PROMPT.md section 5.2) is in `:domain` (`[^a-z0-9]` -> space, ae/oe/ue -> a/o/u);
  the Python builder applies the identical function: `tools/food-db-builder/normalize_cases.json` is run by both
  `test_normalize.py` and `TextNormalizerSharedCasesTest` (`:domain` copies it into its test resources).
- **Food database (Phase 4):** `tools/food-db-builder` builds `foods.db` (BLS 4.0 + Open Food Facts Germany). The full
  DB has 228,871 foods in 59.1 MB (limit 120 MB); filters and their counts are in `out/foods-full.report.txt` and the README.
  The builder creates the tables from Room's exported `createSql`/`setupQueries` (identity hash in `room_master_table`,
  `PRAGMA user_version`), so changing a food entity means: bump `FoodDatabase.VERSION`, rebuild both DBs, commit the new
  schema JSON. `test_build_db.py` fails if builder and schema drift apart.
  - **Packaging:** `-PfoodDb=sample` (default) or `full` copies `tools/food-db-builder/out/foods-<x>.db` to
    `assets/databases/foods.db` through `CopyFoodDbTask` (generated asset dir per variant). Release with the sample logs a
    warning. Room opens it with `createFromAsset` and `fallbackToDestructiveMigration(dropAllTables = true)` (read-only,
    no user data in it, so a new asset version simply replaces the copy).
  - **Identity:** a food is referenced by `FoodKey(source, ref)`: BLS code, OFF barcode or custom food id. Row ids change
    with every rebuild and are never stored. Diary entries and favorites store the key; entries also snapshot the nutrients.
  - **Ids and ranking:** BLS rows first, then OFF by falling popularity, so `ORDER BY id` is relevance and OFF needs no sort.
    FTS4 indexes only the normalized `search_text` (name + brand); `searchBls` orders `starts-with-query, name length, id`,
    then `FoodSearch.rank` (domain) builds the final list in the four tiers of PROMPT.md 5.2 (own + logged in the last 30 days
    by frequency, BLS first word equals the query's first word, other BLS, OFF). Result limit 50, candidates 120 per source.
  - **Unknown stays unknown:** missing nutrients are NULL, never 0. OFF rows need energy (kcal, else kJ/4.184), kcal and kJ must
    agree, 4/4/9 (alcohol 7) plausibility, valid GTIN, name with a letter; liquids are per 100 ml, salt = sodium x 2.5.
  - **Portions:** `food_portions` (Stück, Scheibe, Glas ...) come from `portions_de.csv` and are all `estimated = true`; they are
    shown as "Stück (mittel) ≈ ca. 150 g", the preview gets "ca." and the amount screen explains the estimate. The README lists
    every estimated portion for review. The pack's own serving of an OFF food is "Portion (x g)" and not an estimate.
  - **Status:** `RoomFoodRepository.prepare()` (called from `KalorienzaehlerApp` and the add/onboarding view models) opens the DB
    on `Dispatchers.IO` in the app scope; `status` is Preparing/Ready/Failed. Lookups wait for Preparing; `DefaultFoodCatalog.search`
    does **not** wait: while Preparing it answers from own foods and the view model searches again when the status changes.
    Failed (e.g. no storage) keeps own foods working and shows a hint. Onboarding shows "Lebensmitteldatenbank wird vorbereitet …".
  - **Add screen:** chips Zuletzt, Favoriten, Eigene (default: the first non-empty one); `AddFoodState.searching` hides the
    "no results" hint while the 150 ms debounce/search is pending; a query needs one word of at least 2 letters. Own foods are
    also found by barcode prefix. The star on the amount screen (`PortionViewModel.isFavorite`) toggles a favorite; entries
    without a food (quick entries) have no star.
  - **Tests:** `RoomFoodRepositoryTest`/`ShippedCatalogTest` open the packaged sample DB with Room under Robolectric (JVM twin of
    the instrumented `ShippedFoodDatabaseTest`, which also checks the 150 ms search budget on a full DB). `FakeFoodRepository`
    mimics the FTS prefix semantics for view-model and flow tests. Measured on the full DB (JVM, whole path incl. ranking):
    worst of 3 runs 5-38 ms for ten typical queries.
- **Day sums** are computed in Kotlin (`DiaryMath`) from the day's entries; SQL range sums come with the statistics (Phase 6).
- The keystore can't live in the ephemeral cloud container. How to handle it is decided in Phase 8.

## Gotchas

- **Cloud environment network policy:** these hosts must be in the environment's Allowed
  domains: `dl.google.com` (Android SDK **and** Google Maven, since `maven.google.com`
  redirects there) and `blsdb.de` (BLS). Reachable already: the Gradle distribution
  (`services.gradle.org` redirects the zip to GitHub releases), Maven Central and Google's
  Maven Central mirror, the Gradle Plugin Portal, PyPI, raw.githubusercontent.com and the OFF
  S3 bucket (`openfoodfacts-ds.s3.eu-west-3.amazonaws.com`).
- The cloud container is ephemeral. Push work before ending a session. The SessionStart hook
  (`.claude/settings.json` → `.claude/hooks/session-start.sh`) runs `scripts/cloud-setup.sh` in
  cloud sessions only (`CLAUDE_CODE_REMOTE=true`), synchronously (~6.5 min on a fresh container,
  under a second once installed) and exports `ANDROID_HOME`. Do **not** reference repo files from
  the environment's *Setup script*: it runs before the repository is cloned.
- `sdkmanager` (cmdline-tools 2026) is deprecated and delegates to the new Android CLI
  (`cmdline-tools/latest/bin/android sdk install …`); `--licenses` is no longer needed but harmless.
- The configuration cache is invalidated whenever the container's proxy port (in
  `JAVA_TOOL_OPTIONS`) changes, i.e. on every new session. Expected, not a bug.
- No `/dev/kvm`, so no emulator or Gradle Managed Devices in the cloud container.
- SDK package ids for new platforms carry a minor version: `platforms;android-37.0` (not `android-37`).
- Compose `ui-test-junit4` pulls Espresso 3.5.0, whose idling calls `InputManager.getInstance()`,
  which no longer exists in the SDK 37 framework (Robolectric fails with `NoSuchMethodException`).
  `espresso-core` 3.7.0 is pinned on the unit-test classpath to fix it.
- Material3 1.4.0's `TopAppBar` is still `@ExperimentalMaterial3Api` (the compiler is the judge,
  not API dumps of later versions).
- Expected lint warnings: `AndroidGradlePluginVersion` (Gradle 9.8.x exists; 9.7.1 is deliberate)
  and `ChromeOsAbiSupport` (release ships only ARM ABIs per PROMPT.md §9).
- Robolectric downloads `android-all-instrumented:17-robolectric-15733970-i7` (~240 MB) from Maven
  Central on the first test run without retrying; `scripts/cloud-setup.sh` prefetches it into `~/.m2`.
- The system Gradle in the container (8.14.3) is too old for AGP 9.x. Always use `./gradlew`.
- Both `repo.maven.apache.org` and `repo1.maven.org` often answer **429** through the container
  proxy; Google's official Maven Central mirror (`maven-central.storage-download.googleapis.com`)
  does not. `scripts/cloud-setup.sh` installs `~/.gradle/init.d/maven-central-mirror.init.gradle.kts`
  (copy in `scripts/gradle/`) so Gradle tries the mirror first; `mavenCentral()` stays the
  fallback. Not needed on a normal machine.
- The wrapper jar is Gradle 9.7.1's official jar (SHA-256 `7a9ce74c…`); the distribution is
  pinned by `distributionSha256Sum`.
- Hilt ViewModels in Robolectric tests run `viewModelScope` on the real main looper: after a click that ends in a
  ViewModel coroutine (undo, save) use `compose.waitUntil { ... }` before asserting on the UI.
- Turbine on a `stateIn(WhileSubscribed)` flow: the first item may already be the loaded value, not the initial `null`.
- Bottom-bar tabs share their label with other buttons ("Heute" is also the date bar): tests match tabs with
  `hasText(...) and isSelectable()`.
- 200 % font-scale tests (`DiaryFontScaleTest`) only see composed nodes: scroll lazy-list items into view with
  `scrollListTo(text)` before `clippedTexts()`. The detector was mutation-checked (`maxLines = 1` on an entry name fails it).
- Searches in UI tests start after a debounce: wait with `compose.waitUntil { onAllNodesWithText(...) ... }` (see
  `waitForFood` in `DiaryFlowTest`); in view-model tests `runTest` skips the delay (virtual time), use `advanceTimeBy`/`runCurrent`.
- `ShippedCatalogTest` must wait for `FoodDbStatus.Ready` before searching, because the catalog skips the DB while Preparing.
- `textsWithBrokenWords()` (next to `clippedTexts()`) fails tests when a word is split across lines ("Kohlenhydr/ate"):
  use `FlowRow` (value wraps below the label) instead of two `weight(1f, fill = false)` texts in a `Row`.
- `:domain` can be tested without the Android SDK: see "Testing `:domain` without the SDK".

## Testing `:domain` without the SDK

When Google Maven is unreachable, the root build cannot configure (AGP is missing), but the
pure-Kotlin module still builds from a throwaway settings file outside the repo:

```kotlin
// <scratch>/settings.gradle.kts (plus an empty build.gradle.kts and a copy of gradle.properties)
dependencyResolutionManagement {
    versionCatalogs { create("libs") { from(files("<repo>/gradle/libs.versions.toml")) } }
}
include(":domain")
project(":domain").projectDir = file("<repo>/domain")
```

Run it with the wrapper's Gradle 9.7.1 (`~/.gradle/wrapper/dists/gradle-9.7.1-bin/*/gradle-9.7.1/bin/gradle`,
present after any `./gradlew --version`):
`gradle -I <repo>/scripts/gradle/maven-central-mirror.init.gradle.kts :domain:test`.

## Open items for later phases

- Phase 7: form drafts (custom food, Schnelleintrag, amount, onboarding, Profil) live in ViewModel state and are lost when
  Android kills the process (the route is restored, the typed text is not): back them with `SavedStateHandle`/savers.

- Phase 5: barcode check-digit validation (`BarcodeNormalizer`), `CustomFoodRoute` gets a barcode prefill, scanner button on the add screen.
- Phase 6: SQL range sums per day for the statistics (`DiaryDao`), tests for them.
- Phase 7: `TodayViewModel.today` is read when the screen is created: refresh at midnight; "Alle Daten loeschen" must also
  call `diary.deleteEverything()`, `customFoods.deleteEverything()` and `favorites.deleteEverything()` (all exist already).

- Phase 7: Material 3's `NavigationBarItem` has a fixed 80 dp height, so tab labels clip at 200 %
  font scale (PROMPT.md §8); needs a custom bar or `ShortNavigationBar` check. `FontScaleTest`
  currently covers the content screens, not the root navigation bar: extend it.
- Phase 7: "Alle Daten löschen" and "Über die App" are not built yet (`FoodRepository.meta()` already returns the
  sources, dates and counts for the About screen; data deletion is Phase 7).
- Phase 5: `FoodRepository.findByBarcodes(listOf(12-digit, 13-digit))` is ready for the scanner; it needs the check-digit logic.
