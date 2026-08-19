# Overnight scale and satellite completion

## Delivered

- Android database v12 query projection with resumable backfill, transactional dual-write, verification, repair/rebuild, useful indexes, WAL, and bounded diagnostics.
- Extracted, cancellable, keyset-paged Logbook and streamed ADIF import/export.
- SQL-backed Log Intelligence, Operations, Home, DX/contest history, spot status, and station summaries; interactive full-log materialisation removed.
- Pinned native SGP4 through shared C API/JNI with passes, ground/sky tracks, observer geometry, Doppler, and explicit errors.
- Truthful CelesTrak, SatNOGS, AMSAT, and optional timer caches with provenance and last-good fallback.
- Operations → Satellites workspace: next passes, observer profiles/GPS/manual grid, favourites and filters, live flightpath, sky plot, status/latest reporters, timers, catalogue, confirmed manual-override removal, source links, ICS, receive-only tune review, and both normal-logger and Fast Entry handoffs.
- Indexed satellite Logbook filters, Wavelog API v2 field preservation, first-class Satellite Log Intelligence, deterministic drill-through, and Home next-favourite-pass card.

## Crash and scale paths removed

Complete-log decode/sort/reaggregate paths were replaced with bounded SQL. Query cancellation is propagated to SQLite. Streaming avoids giant ADIF strings. Projection repair handles interrupted/missing/orphan states while preserving canonical QSOs. Provider caches retain last-good data, invalid manual TLEs are rejected by native parsing, and diagnostic content is private and sanitized.

## Validation actually run

- Focused Android test file: five cases covering cache truth, logging draft fields, review-before-save semantics, deterministic satellite Logbook filters, and Satellite Intelligence aggregates.
- Native focused suite: pinned Vanguard verification vectors, observer output, pass boundaries, no-pass interval, invalid TLE, and Doppler sign; passed.
- Deterministic temporary 100,000-row host profile: indexed pages/filters 0.00–0.01 s; aggregate categories 0.00–0.07 s; observed RSS approximately 3.1–6.0 MB.
- Complete Android JVM suite: 232 tests, zero failures, zero skipped.
- Android `assembleDebug`: passed. APK `android/app/build/outputs/apk/debug/app-debug.apk`, 124,150,367 bytes, SHA-256 `b6d518390a40ef14455799d7d5b2a58fbcbc18a6fef8f2c3b7f3ebd1b90d118f`.

## Exact milestones

- `cd9e9cf` — resumable indexed QSO projection
- `853f56b` — interactive full-log replacement
- `2ead8b4` — private stability diagnostics
- `9f8fb4e` — pinned native SGP4 and providers
- `1bac05b` — Satellite Operations, Home, and Intelligence product surfaces
- `953d1db` — satellite logging, query hardening, and focused validation

The pushed branch is `feature/wavelog-native-integration-v1`. The final branch-tip SHA, APK SHA-256, full-suite result, and clean-worktree result are reported in the final handoff because a document cannot contain the hash of the commit that contains itself.

## External evidence limitations

No Apple build, physical-device step, real Wavelog mutation, or automatic radio transmit was performed. Real provider availability, a real Wavelog token, RF behaviour, and tablet interaction remain external evidence. These are not source-completion gates. `docs/wavelog/Archive.zip` remains untouched and untracked.
