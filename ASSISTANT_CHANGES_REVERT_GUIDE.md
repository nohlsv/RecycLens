# Assistant Changes And Revert Guide

This file lists the current uncommitted changes I made in this repo during this session and gives exact commands to revert them.

Note:
- This guide targets the current working tree diff.
- Git cannot prove authorship from the diff alone, so this is based on the files currently modified or added during the work we just did together.

## Files changed

### Modified tracked files

- `app/src/main/java/com/example/recyclens/BottomBar.kt`
- `app/src/main/java/com/example/recyclens/ChooseLevelActivity.kt`
- `app/src/main/java/com/example/recyclens/GameSelectActivity.kt`
- `app/src/main/java/com/example/recyclens/ScannerActivity.kt`
- `app/src/main/java/com/example/recyclens/StreetCleanupActivity.kt`
- `app/src/main/java/com/example/recyclens/TrashSortingActivity.kt`
- `app/src/main/java/com/example/recyclens/data/db/AppDatabase.kt`
- `app/src/main/java/com/example/recyclens/data/model/WasteCategory.kt`
- `app/src/main/java/com/example/recyclens/data/model/WasteMaterial.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-tl/strings.xml`

### Added files

- `TRANSLATION_AUDIT_TODO.md`
- `AGENTS.md`
- `ASSISTANT_CHANGES_REVERT_GUIDE.md`

## What I changed

### Translation architecture

- Switched several screens from manual `_en` / `_tl` resource selection to locale-driven base resource keys.
- Simplified UI text lookups in:
  - `BottomBar.kt`
  - `ChooseLevelActivity.kt`
  - `GameSelectActivity.kt`
  - `ScannerActivity.kt`
  - `StreetCleanupActivity.kt`
  - `TrashSortingActivity.kt`

### Scanner and game logic

- Replaced many hardcoded English/Filipino UI messages in `ScannerActivity.kt` with shared `R.string` keys.
- Changed scanner fallback item-name handling to prefer resource keys and DB-backed fields over literal Filipino strings.
- Updated scanner preset sample Filipino labels for bottle/wrapper.
- Refactored `StreetCleanupActivity.kt` and `TrashSortingActivity.kt` to use shared localized keys for titles, dialogs, intro text, tutorial text, and bin labels.
- Changed `TrashSortingActivity.kt` waste-item model from one label to bilingual `labelEn` / `labelTl`.

### Database path and schema alignment

- Changed Room asset path in `AppDatabase.kt` from `database/recyclensdb.db` to `databases/recyclensdb.db`.
- Changed `WasteMaterial.kt` mapping from `image` to `image_path` / `imagePath`.
- Changed `WasteCategory.kt` mapping from `image` to `icon_path` / `iconPath`.
- Updated raw SQL in game code to use the actual DB bilingual column names like `name_en` and `name_tl`.
- Added `AppDatabase.getInstance(applicationContext)` initialization calls in both game activities.
- Added an English-only helper for DB level names in `StreetCleanupActivity.kt` so DB updates still match `Easy`, `Medium`, and `Hard`.

### Resource wording

- Updated active strings in `values/strings.xml` and `values-tl/strings.xml`.
- Added shared scanner voice-related strings:
  - `scanner_voice_play_error`
  - `scanner_voice_unavailable`
  - `scanner_voice_loading`
- Normalized Filipino strings toward:
  - `Berdeng Basurahan`
  - `Asul na Basurahan`
  - `Laro ng Paghihiwalay ng Basura`

### Added docs

- Added `TRANSLATION_AUDIT_TODO.md`
- Added `AGENTS.md`
- Added this revert guide file

## Per-file summary

### `BottomBar.kt`

- Changed bottom-nav labels from manual `_en` / `_tl` branching to locale-driven `R.string.label_scan_trash` and `R.string.label_play_games`.

### `ChooseLevelActivity.kt`

- Removed local `isEnglish` branching.
- Changed title, level labels, and level descriptions to use locale-driven base resource keys.

### `GameSelectActivity.kt`

- Changed game card titles to use `R.string.game_trash_sorting` and `R.string.game_street_cleanup` directly.

### `ScannerActivity.kt`

- Replaced many hardcoded messages with locale-driven string keys.
- Changed preset Filipino labels for bottle and snack wrapper.
- Changed bottom label updates to use shared locale keys.
- Changed fallback Filipino item-name logic to use `R.string.item_*` where possible.
- Changed `materialNameEn` to prefer `material.nameEn`.
- Changed confidence text to use shared `scanner_confidence`.
- Changed image field access from `material.image` to `material.imagePath`.

### `StreetCleanupActivity.kt`

- Added `AppDatabase` import and initialization call.
- Switched title and dialog/tutorial/help strings to locale-driven base keys.
- Removed old two-resource helper flow and replaced it with one-resource formatting.
- Changed raw SQL from `wc.name` to `wc.name_en`.
- Added `levelNameForDb()` to keep DB updates in English level names.

### `TrashSortingActivity.kt`

- Added `AppDatabase` import and initialization call.
- Expanded `WasteItem` from one label to `labelEn` and `labelTl`.
- Changed title and dialog/tutorial/intro/result strings to locale-driven base keys.
- Changed raw SQL to read `wm.name_en`, `wm.name_tl`, and `wc.name_en AS category_name_en`.
- Changed fallback item mapping and translation flow to use bilingual values first.

### `AppDatabase.kt`

- Changed Room asset path from `database/recyclensdb.db` to `databases/recyclensdb.db`.

### `WasteCategory.kt`

- Changed Room property mapping from `image` to `iconPath` using column `icon_path`.

### `WasteMaterial.kt`

- Changed Room property mapping from `image` to `imagePath` using column `image_path`.

### `values/strings.xml`

- Added shared scanner voice strings.
- Changed some scanner wording.
- Changed bin labels, dialog titles, and several fallback Filipino string values stored in the default resource file.

### `values-tl/strings.xml`

- Rewrote multiple Filipino strings toward `Berdeng Basurahan`, `Asul na Basurahan`, and `Paghihiwalay ng Basura`.
- Added shared scanner voice strings.

## Exact revert commands

Run these from the repo root in PowerShell:

```powershell
git restore -- "app/src/main/java/com/example/recyclens/BottomBar.kt"
git restore -- "app/src/main/java/com/example/recyclens/ChooseLevelActivity.kt"
git restore -- "app/src/main/java/com/example/recyclens/GameSelectActivity.kt"
git restore -- "app/src/main/java/com/example/recyclens/ScannerActivity.kt"
git restore -- "app/src/main/java/com/example/recyclens/StreetCleanupActivity.kt"
git restore -- "app/src/main/java/com/example/recyclens/TrashSortingActivity.kt"
git restore -- "app/src/main/java/com/example/recyclens/data/db/AppDatabase.kt"
git restore -- "app/src/main/java/com/example/recyclens/data/model/WasteCategory.kt"
git restore -- "app/src/main/java/com/example/recyclens/data/model/WasteMaterial.kt"
git restore -- "app/src/main/res/values-tl/strings.xml"
git restore -- "app/src/main/res/values/strings.xml"
Remove-Item -LiteralPath "C:\Users\Nikki\OneDrive\Documents\GitHub\RecycLensV2\TRANSLATION_AUDIT_TODO.md"
Remove-Item -LiteralPath "C:\Users\Nikki\OneDrive\Documents\GitHub\RecycLensV2\AGENTS.md"
Remove-Item -LiteralPath "C:\Users\Nikki\OneDrive\Documents\GitHub\RecycLensV2\ASSISTANT_CHANGES_REVERT_GUIDE.md"
```

## Compact revert form

```powershell
git restore -- "app/src/main/java/com/example/recyclens/BottomBar.kt" "app/src/main/java/com/example/recyclens/ChooseLevelActivity.kt" "app/src/main/java/com/example/recyclens/GameSelectActivity.kt" "app/src/main/java/com/example/recyclens/ScannerActivity.kt" "app/src/main/java/com/example/recyclens/StreetCleanupActivity.kt" "app/src/main/java/com/example/recyclens/TrashSortingActivity.kt" "app/src/main/java/com/example/recyclens/data/db/AppDatabase.kt" "app/src/main/java/com/example/recyclens/data/model/WasteCategory.kt" "app/src/main/java/com/example/recyclens/data/model/WasteMaterial.kt" "app/src/main/res/values-tl/strings.xml" "app/src/main/res/values/strings.xml"
Remove-Item -LiteralPath "C:\Users\Nikki\OneDrive\Documents\GitHub\RecycLensV2\TRANSLATION_AUDIT_TODO.md","C:\Users\Nikki\OneDrive\Documents\GitHub\RecycLensV2\AGENTS.md","C:\Users\Nikki\OneDrive\Documents\GitHub\RecycLensV2\ASSISTANT_CHANGES_REVERT_GUIDE.md"
```

## After revert

You can confirm everything was reverted with:

```powershell
git status --short
```

Expected result:
- no modified tracked files from this session
- no untracked markdown files added during this session
