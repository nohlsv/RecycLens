# English-Filipino Translation Audit TODO

## Program map

- [x] Identify the main app flow.
  `MainActivity` -> `ScannerActivity` -> `GameSelectActivity` -> `ChooseLevelActivity` -> `TrashSortingActivity` / `StreetCleanupActivity`
- [x] Identify translation control points.
  `LanguagePrefs.kt`, `BottomBar.kt`, `WasteCatalog.kt`, `GameDatabase.kt`, `values/strings.xml`, `values-tl/strings.xml`
- [x] Identify data sources used for translated content.
  `ScannerActivity.kt`, `AppDatabase.kt`, `GameDatabase.kt`, `RecycLensDao.kt`, `assets/databases/recyclensdb.db`
- [x] Identify non-code translation references.
  `bilingual.md`, `bilingguwal.md`

## Findings

### High priority

- [x] `AppDatabase` points to the wrong asset path.
  In [AppDatabase.kt](app/src/main/java/com/example/recyclens/data/db/AppDatabase.kt), `createFromAsset("database/recyclensdb.db")` does not match the actual file at `app/src/main/assets/databases/recyclensdb.db`.
  Impact: scanner lookups for English/Filipino names and category descriptions can fail and silently fall back to hardcoded guesses.

- [x] The project uses two localization systems at the same time.
  There is proper Android locale support through `values/strings.xml` and `values-tl/strings.xml`, but many screens still manually branch on `_en` / `_tl` resource IDs in:
  `BottomBar.kt`, `GameSelectActivity.kt`, `ChooseLevelActivity.kt`, `ScannerActivity.kt`, `TrashSortingActivity.kt`, `StreetCleanupActivity.kt`.
  Impact: duplicated strings, inconsistent behavior, and harder maintenance.

- [x] The SQLite/Room schema is inconsistent across the codebase.
  `WasteMaterial.kt` and `WasteCategory.kt` expect bilingual columns like `name_en`, `name_tl`, `description_en`, `description_tl`, while `DatabaseHelper.kt` creates older single-language columns like `name`, `description`, `bin_color`, `icon_path`.
  Impact: one part of the app expects a bilingual schema, another part can create a different schema entirely.

- [x] Some game screens use `openOrCreateDatabase("recyclensdb.db", ...)` directly instead of the Room asset database.
  Seen in `TrashSortingActivity.kt` and `StreetCleanupActivity.kt`.
  Impact: if the asset DB is not copied first, Android can create an empty local DB and the app silently falls back to hardcoded item lists.

### Medium priority

- [x] Filipino strings are partly untranslated or mixed-language.
  Examples in [values/strings.xml](app/src/main/res/values/strings.xml) and [values-tl/strings.xml](app/src/main/res/values-tl/strings.xml):
  `street_green_bin_text_tl = "green bin"`
  `street_blue_bin_text_tl = "blue bin"`
  Several Filipino instructions still include English terms like `food waste`, `plastic`, `blue bin`, `green bin`, `street cleanup game`, `trash sorting game`.
  Impact: the Filipino mode is not linguistically consistent.

- [x] Scanner translations are partly hardcoded in Kotlin instead of resources or DB.
  `getTagalogMaterialName()` in [ScannerActivity.kt](app/src/main/java/com/example/recyclens/ScannerActivity.kt) contains manual English -> Filipino mappings.
  Impact: translations are duplicated across code, strings resources, and database content.

- [x] There are mojibake / encoding issues in user-facing text and docs.
  Examples:
  `Iâ€™m checking what kind of waste this is.`
  `Biodegradable â€“ GREEN bin`
  Similar corruption appears in `bilingual.md` and `bilingguwal.md`.
  Impact: visible text quality issue and poor validator-facing documentation quality.

- [x] `ScannerActivity` manages its own language toggle logic instead of sharing the bottom bar behavior.
  `ScannerActivity` duplicates language UI updates and state handling rather than implementing the same `BottomBar.LanguageAware` flow as the other screens.
  Impact: scanner language behavior can drift from the rest of the app.

### Low priority

- [x] The project keeps duplicate strings that are functionally the same.
  Examples:
  `label_easy` and `label_easy_en`
  `street_intro` and `street_intro_en`
  `trash_result_success_speech` and `trash_result_success_speech_en`
  Impact: higher chance of one variant being edited while others are left stale.

- [x] Translation coverage is not protected by tests.
  There are only default example tests under `app/src/test` and `app/src/androidTest`.
  Impact: missing strings, wrong asset paths, and fallback regressions are easy to ship.

## TODO checklist

### Locale architecture

- [x] Pick one localization strategy and remove the other.
  Kept Android locale resources (`values` / `values-tl`) for UI copy and removed the custom `_en` / `_tl` branching path from active screen code, including `ScannerActivity`.
- [x] Move all UI copy to string resources.
- [x] Remove hardcoded Filipino phrases from Kotlin where possible.
  Replaced the manual scanner fallback map with the shared `WasteCatalog` source of truth.
- [x] Make `ScannerActivity` use the same language-change flow as the rest of the app.

### Database and content

- [x] Fix the Room asset path to `databases/recyclensdb.db`.
- [x] Verify the shipped database schema matches the Room entities exactly.
  Verified on April 20, 2026 against `app/src/main/assets/databases/recyclensdb.db` with a local SQLite inspection: `waste_category(category_id,name_en,name_tl,bin_color,icon_path,description_en,description_tl)` and `waste_material(material_id,name_en,name_tl,image_path,category_id)`.
- [x] Decide whether games should use Room or raw SQLite and standardize on one.
  Standardized on raw SQLite only for game-table queries and updates, but always against the Room-managed packaged DB through `GameDatabase`.
- [x] Remove or refactor `DatabaseHelper.kt` if it no longer matches the actual schema.
- [x] Confirm whether item names and category descriptions should come from DB, strings.xml, or both.
  Runtime item/category contracts now come from the packaged DB plus the shared `WasteCatalog`; static UI copy stays in Android string resources.

### Translation quality

- [x] Replace mixed-language Filipino copy with consistent Filipino or approved classroom terminology.
- [x] Fix untranslated Filipino strings such as `green bin` and `blue bin`.
- [x] Normalize waste item names across scanner, games, DB, and documentation.
  Normalized the audited runtime/documentation set around `Bottle/Bote`, `Candy Wrapper/Balot ng Kendi`, and `Styrofoam Box/Styro na Lalagyan`.
- [x] Fix all mojibake / encoding issues in XML and Markdown files.
  Rewrote `bilingual.md` and `bilingguwal.md` as clean UTF-8 markdown references.
- [x] Decide whether the target language label should be `Filipino`, `Tagalog`, or `tl-PH` in the UI and docs.
  Chosen repo-wide label: `Filipino`. The short UI toggle now uses `FIL`.

### Scanner-specific review

- [x] Verify every predicted label maps to one English name and one Filipino name.
  Centralized prediction aliases in `WasteCatalog` so scanner labels resolve through one reviewed mapping table.
- [x] Verify fallback category detection matches the actual DB categories.
- [x] Replace manual `getTagalogMaterialName()` logic with a single source of truth.
- [ ] Verify TTS behavior for `fil-PH` and `tl-PH` devices.
  Blocked on device/emulator verification.

### Game-specific review

- [x] Make sure item names shown in games come from the same translation source as scanner results.
- [x] Verify level names and result dialogs are fully localized.
  Verified by code-path review in `StreetCleanupActivity`/`TrashSortingActivity` plus locale-differentiation instrumentation checks.
- [x] Verify DB-backed item loading still works when the packaged DB is present.
  Added JVM smoke coverage in `DatabaseAssetSmokeTest` that queries packaged DB rows used by scanner and game flows.

### Verification

- [x] Add unit/instrumentation checks for locale switching.
  Added `LocaleSwitchingInstrumentedTest` for EN/TL resource switching checks across core game/result strings.
- [x] Add a string parity check so key names exist in both `values` and `values-tl`.
- [x] Add a smoke test for the scanner DB lookup path.
  Added `DatabaseAssetSmokeTest` with SQLite JDBC query checks against `app/src/main/assets/databases/recyclensdb.db`.
- [ ] Build the app after fixing environment setup.

## Build/test status

- [x] Attempted verification with Gradle.
  `./gradlew.bat :app:testDebugUnitTest` was attempted on April 20, 2026.
  Gradle now starts, but the run fails before task execution because the current `JAVA_HOME` is JDK `25.0.2`, which is not accepted by the Kotlin/Gradle toolchain in this project (`IllegalArgumentException: 25.0.2`).
  A full Gradle build/test result is still unverified here.

## Decisions Applied

- [x] Use `Filipino` consistently in the UI and validator-facing docs unless a future requirement explicitly asks for `Tagalog`.
- [x] Prefer classroom-friendly Filipino copy over mixed Filipino + English when there is already a reviewed Filipino term.
- [x] Treat `app/src/main/assets/databases/recyclensdb.db` as the authoritative runtime data contract.
- [x] Proceed with translation architecture fixes in code instead of documentation-only cleanup.

## Remaining Manual Verification

- [ ] Open the scanner screen and confirm English and Filipino both re-render correctly after toggling the bottom bar.
- [ ] Confirm TTS on a real device for both `fil-PH` and `tl-PH` fallback behavior.
- [ ] Open both games on a fresh install path and confirm DB-backed item loading, localized result dialogs, and score updates still work end to end.
- [ ] Run Gradle build/tests once the environment uses a supported JDK (for example JDK 17 or 21) and Android SDK toolchain.

## Translation Double-Check (April 20, 2026)

### Verified now

- [x] Key parity still holds between `values/strings.xml` and `values-tl/strings.xml`.
- [x] Active Kotlin UI code is mostly locale-driven via base `R.string.*` keys.
- [x] No active screen flow was found still branching on `_en` / `_tl` resource IDs for normal UI labels.

### New TODO from this re-check

- [ ] Replace remaining hardcoded bilingual sample labels in `ScannerActivity.PresetSample` with `WasteCatalog` or `R.string` lookups to prevent drift.
- [ ] Decide whether `scanner_header` in `values-tl/strings.xml` should be Filipino text instead of `Scanner Page`.
- [ ] Decide whether `info_title` should remain bilingual (`Non-Biodegradable\nHindi Nabubulok`) or be Filipino-only in the Filipino locale.
- [ ] Consolidate or retire legacy duplicate key families (`*_en`, `*_tl`) that are no longer used in active UI code.
- [ ] Keep DB contract literals (`Street Cleanup`, `Trash Sorting`, `Easy/Medium/Hard`) centralized in one helper/constant owner so they do not drift across files.

## Anti-Hallucination / Anti-Drift Guardrails

Use this checklist before any future translation edit:

- [ ] Source of truth first: check `values/strings.xml`, `values-tl/strings.xml`, `WasteCatalog`, and packaged DB (`app/src/main/assets/databases/recyclensdb.db`) before changing wording.
- [ ] Never invent Filipino labels in feature code when a canonical resource or catalog entry exists.
- [ ] If wording changes in one place, sync all authoritative mirrors in the same change: resources, `WasteCatalog`, and validator docs (`bilingual.md`, `bilingguwal.md`) when applicable.
- [ ] Prefer base semantic keys (`R.string.foo`) over language-encoded key selection in Kotlin.
- [ ] Keep one mapper only for fallback naming/category detection (`WasteCatalog`) and do not add parallel heuristics.
- [ ] Validate with automated checks when available: string-key parity test, locale-switching instrumentation test, and DB smoke test.
- [ ] Do not claim build/test success unless a real Gradle run succeeds in the current environment.
