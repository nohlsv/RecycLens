# Application Bug Audit (April 20, 2026)

## Scope

Static code/runtime-path review of the Android app sources under `app/src/main`.

## Fixed in this pass

1. Scanner lifecycle hardening
   - Removed unused secondary model fields/constants that could drift from actual model path.
   - Guarded interpreter cleanup so `onDestroy()` does not crash if model init failed early.
   - File: `app/src/main/java/com/example/recyclens/ScannerActivity.kt`

2. Receiver registration compatibility
   - Updated app-level broadcast receiver registration to use Android 13+ receiver flags.
   - File: `app/src/main/java/com/example/recyclens/RecycLensApp.kt`

3. DB cursor safety + visibility
   - Replaced `getColumnIndex(...)` with `getColumnIndexOrThrow(...)` in game loading paths.
   - Added error logging for previously swallowed DB exceptions in Street Cleanup flows.
   - Files:
     - `app/src/main/java/com/example/recyclens/StreetCleanupActivity.kt`
     - `app/src/main/java/com/example/recyclens/TrashSortingActivity.kt`

## Remaining potential issues (recommended next fixes)

1. Locale refresh completeness risk
   - Bottom-bar language toggle updates selected views via `onLanguageChanged()` but does not recreate activities.
   - Some XML-bound text not explicitly refreshed in each activity can remain stale until reopen.
   - Candidate files:
     - `app/src/main/java/com/example/recyclens/BottomBar.kt`
     - `app/src/main/java/com/example/recyclens/ScannerActivity.kt`
     - `app/src/main/java/com/example/recyclens/StreetCleanupActivity.kt`
     - `app/src/main/java/com/example/recyclens/TrashSortingActivity.kt`

2. Destructive migration data-loss risk
   - Room still uses `fallbackToDestructiveMigration()`.
   - Any schema version bump mismatch can wipe local DB state.
   - File: `app/src/main/java/com/example/recyclens/data/db/AppDatabase.kt`

3. Deprecated display rotation API
   - Camera capture rotation still reads `windowManager.defaultDisplay.rotation`.
   - Recommended to migrate to display APIs that are stable for modern SDK targets.
   - File: `app/src/main/java/com/example/recyclens/ScannerActivity.kt`

4. Unused service/dead path
   - `MusicService` is declared in manifest but app flow uses `MusicManager` directly.
   - Keep one authoritative audio path to reduce lifecycle bugs.
   - Files:
     - `app/src/main/AndroidManifest.xml`
     - `app/src/main/java/com/example/recyclens/MusicService.kt`

5. Build verification blocked by environment
   - Full Gradle validation remains blocked by unsupported JDK in current environment (`25.0.2`).
   - Use JDK 17 or 21 for project builds/tests.

## Verification notes

- Editor diagnostics for changed files: no errors.
- Full Gradle run not re-attempted in this pass due known JDK blocker.
