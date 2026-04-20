# AGENTS.md

This file defines repo-local rules for agents and contributors working in `RecycLensV2`.

The goal is simple: do not drift from the codebase, do not invent facts, and do not create parallel sources of truth.

## Core rules

1. Prefer existing source-of-truth data over inference.
   If the repo already has a resource string, DB field, schema file, or documented wording, use that first.

2. Do not guess when the repo can answer the question.
   Check the actual file, schema, resource, SQL query, or asset path before making a claim.

3. Do not create parallel implementations for the same behavior.
   If a feature already uses Android locale resources, do not add a second manual translation path in Kotlin.

4. Keep one authoritative source per kind of content.
   UI copy, scanner labels, DB-backed names, and validator-facing documentation must each have a defined owner.

5. State uncertainty explicitly.
   If something cannot be verified locally, say so and identify what is missing.

## Translation rules

1. Use Android locale resources as the default source for static UI text.
   Static user-facing copy belongs in:
   - `app/src/main/res/values/strings.xml`
   - `app/src/main/res/values-tl/strings.xml`

2. Do not add new parallel string families like `foo`, `foo_en`, and `foo_tl` for the same label.
   Use semantic keys like `label_scan_trash` and let locale selection resolve the correct value.

3. Do not branch on `LanguagePrefs.isEnglish()` just to choose translated UI text.
   Locale switching should flow through Android resources unless there is a documented exception.

4. Keep `values/` and `values-tl/` synchronized.
   If a key is added or changed in one locale, update the paired locale in the same change.

5. Do not ship mixed-language placeholders as finished translations.
   Filipino UI copy must be reviewed as Filipino UI copy, not as English with a few replaced words.

6. Use the repo’s validator-facing references when wording matters.
   Translation-sensitive edits should be checked against:
   - `bilingual.md`
   - `bilingguwal.md`

7. In UI and documentation for this repo, prefer `Filipino` as the language name unless the user instructs otherwise.

## Database rules

1. Treat the packaged database as a contract.
   Before changing entities, DAO queries, or raw SQL, inspect the shipped asset database and confirm the real schema.

2. Keep Room entities, DAO queries, and raw SQL aligned.
   A DB change is incomplete if any of these still point at old column names or tables.

3. Do not maintain competing schemas for the same runtime data.
   If `AppDatabase`, `DatabaseHelper`, and raw SQL disagree, fix the disagreement instead of adding more adapters.

4. If raw SQL remains in feature code, validate table names and columns against the packaged DB before merge.

5. Do not rely on destructive migration as proof the schema is correct.
   `fallbackToDestructiveMigration()` is not a substitute for validating the schema and data path.

## Anti-hallucination rules for labels and content

1. Do not infer item names, translations, or categories from substrings if authoritative data exists.
   Use canonical DB rows, resource keys, or documented mappings first.

2. If fallback mapping is unavoidable, keep it centralized.
   Do not duplicate translation heuristics across scanner, game, and dialog code.

3. Unknown data should fail visibly or fall back to one reviewed generic label.
   Do not invent a new English or Filipino item name just to make the UI look complete.

4. When a value is inferred, mark it mentally as inferred and try to replace it with verified data before finalizing the change.

## Resource and code rules

1. Prefer semantic resource keys over language-encoded keys.
2. Prefer `R.string` over hardcoded UI text.
3. Prefer one shared mapper over multiple local heuristics.
4. Prefer updating existing flows over adding alternative flows.

## Verification rules

1. No translation or schema change is complete without verification.

2. Minimum checks for translation changes:
   - Verify the same screen in English and Filipino.
   - Confirm the code is not still manually selecting `_en` / `_tl` keys where locale resources should apply.
   - Confirm the target locale does not contain mixed-language leftovers.

3. Minimum checks for DB changes:
   - Verify the packaged asset exists at the expected path.
   - Verify DAO queries and raw SQL target the same schema.
   - Verify scanner lookup and game flows still resolve DB-backed content on a fresh install path.

4. Never claim a build or test passed unless it actually ran.
   If the Android SDK is not configured, say that verification is blocked and do not overstate confidence.

## Documentation and encoding rules

1. Preserve UTF-8 text integrity.
   Do not introduce or ignore mojibake such as corrupted apostrophes or dashes.

2. Keep terminology aligned across:
   - resource strings
   - Kotlin fallback logic
   - packaged DB content
   - `bilingual.md`
   - `bilingguwal.md`

3. If wording changes in one of those sources, check whether the others now disagree.

## Default workflow

1. Inspect the relevant files first.
2. Identify the current source of truth.
3. Make the smallest change that moves the repo toward one authoritative path.
4. Verify the change with the strongest check available in the environment.
5. Report what was verified and what remains unverified.

## Hard stops

Stop and reassess if any of these are true:

- A change would introduce a second source of truth.
- A translation is being guessed instead of verified.
- A DB query is being changed without checking the actual schema.
- A claim about build/test success cannot be backed by a real run.
- A fallback heuristic is about to replace available canonical data.
