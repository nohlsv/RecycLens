# English-Filipino Translation Audit TODO

## Program map

- [x] Identify the main app flow.
  `MainActivity` -> `ScannerActivity` -> `GameSelectActivity` -> `ChooseLevelActivity` -> `TrashSortingActivity` / `StreetCleanupActivity`
- [x] Identify translation control points.
  `LanguagePrefs.kt`, `BottomBar.kt`, `values/strings.xml`, `values-tl/strings.xml`
- [x] Identify data sources used for translated content.
  `ScannerActivity.kt`, `AppDatabase.kt`, `RecycLensDao.kt`, `DatabaseHelper.kt`, `assets/databases/recyclensdb.db`
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

- [ ] Pick one localization strategy and remove the other.
  Recommendation: keep Android locale resources (`values` / `values-tl`) and stop branching on `_en` / `_tl` IDs in code.
- [ ] Move all UI copy to string resources.
- [ ] Remove hardcoded Filipino phrases from Kotlin where possible.
- [ ] Make `ScannerActivity` use the same language-change flow as the rest of the app.

### Database and content

- [ ] Fix the Room asset path to `databases/recyclensdb.db`.
- [ ] Verify the shipped database schema matches the Room entities exactly.
- [ ] Decide whether games should use Room or raw SQLite and standardize on one.
- [ ] Remove or refactor `DatabaseHelper.kt` if it no longer matches the actual schema.
- [ ] Confirm whether item names and category descriptions should come from DB, strings.xml, or both.

### Translation quality

- [ ] Replace mixed-language Filipino copy with consistent Filipino or approved classroom terminology.
- [ ] Fix untranslated Filipino strings such as `green bin` and `blue bin`.
- [ ] Normalize waste item names across scanner, games, DB, and documentation.
- [ ] Fix all mojibake / encoding issues in XML and Markdown files.
- [ ] Decide whether the target language label should be `Filipino`, `Tagalog`, or `tl-PH` in the UI and docs.

### Scanner-specific review

- [ ] Verify every predicted label maps to one English name and one Filipino name.
- [ ] Verify fallback category detection matches the actual DB categories.
- [ ] Replace manual `getTagalogMaterialName()` logic with a single source of truth.
- [ ] Verify TTS behavior for `fil-PH` and `tl-PH` devices.

### Game-specific review

- [ ] Make sure item names shown in games come from the same translation source as scanner results.
- [ ] Verify level names and result dialogs are fully localized.
- [ ] Verify DB-backed item loading still works when the packaged DB is present.

### Verification

- [ ] Add unit/instrumentation checks for locale switching.
- [ ] Add a string parity check so key names exist in both `values` and `values-tl`.
- [ ] Add a smoke test for the scanner DB lookup path.
- [ ] Build the app after fixing environment setup.

## Build/test status

- [x] Attempted verification with Gradle.
  `./gradlew.bat test` failed because Android SDK location is not configured.
  `./gradlew.bat assembleDebug` failed for the same reason.

## Clarifications needed

- [ ] Should the app use `Filipino` or `Tagalog` consistently in the thesis/documents and UI?
- [ ] Do you want classroom-friendly Filipino only, or is mixed Filipino + common English terms acceptable for kindergarten users?
- [ ] Is `app/src/main/assets/databases/recyclensdb.db` the authoritative source of truth, or should strings/resources override it?
- [ ] Do you want me to do the next step as a documentation cleanup only, or should I start fixing the translation architecture in code?
