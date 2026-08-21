# Neural empirical outlook final hardening

## Scope and refs

- Frozen main: `909328a1c318db9252c626a6e0fccc73b66e22ab`.
- Feature start: `0059b0fa94bd31f018ec9dddc49a1aa49e18a3bc`.
- Validated source commit: `2d8e465` (`fix(neural): harden outlook persistence and recovery`).
- Final documentation and branch-head SHAs are reported in the handoff because a commit cannot record its own hash.

No Apple/shared-core source, dependency, provider fetch contract, product section, transmit boundary or protected device state changed.

## Schema 5 and bounded persistence

Schema 5 adds `receiver_keys` to `evidence_bucket`. The v4→v5 migration preserves the existing spot/outlook tables and rows, moves historical cluster buckets to `global|cluster-history|v1`, quarantines old broad pending predictions as `UNVERIFIABLE`, and initializes daily compaction metadata. Live buckets remain station-scoped; only the globally sourced journal baseline is shared.

Persisted verification forecasts must be global, non-`INSUFFICIENT_EVIDENCE`, supported by the band gate, have current observations and name at least one actual contributing source family. Regional 72-cell forecasts remain display-only. IDs are deterministic by model, station, ceiling-aligned 15-minute target slot, window and band, so repeated five-minute calculations do not multiply rows.

Verified outcomes retain 14 days. Evidence and pending rows retain 180 days. Calibration aggregates are durable. Daily compaction enforces 100,000 predictions by removing oldest verified rows first and then oldest ended pending rows; it never removes unended pending rows.

## Verification, backfill and lifecycle

Verification unions exact bounded callsign hashes across all target-window buckets per contributing source family. A hit is two calls in one contributing family or one call in two contributing families. A miss needs `CURRENT`/`CACHED` heartbeat coverage from a contributing family; unrelated coverage cannot convert an outage into a miss. Unverifiable outcomes do not increment verified or hit calibration counters.

Backfill reads at most 1,000 frozen-cutoff journal rows, unions capped exact call and receiver hashes with any prior bucket, and commits both aggregates and `rowid` progress in one transaction. One batch precedes the first partial outlook; foreground work continues at one batch per five seconds. Historical cluster data is shared globally rather than copied per station.

The application-scoped controller uses `SupervisorJob`, a conflated wake channel and one long-lived worker. A local five-minute heartbeat records source states and recomputes without fetching providers. Failures retain the latest input, surface a sanitized retry status and retry after five seconds. Close is idempotent. Calibration is read from the selected window/band score bin, and missing heartbeat ages are reported as unavailable.

## Deterministic validation

Eight new cases use the existing two focused test files. They cover eligibility exclusions, regional exclusion, 15-minute deduplication, exact capped keys, contributing-source verification, selected calibration, a 1,000-row backfill boundary with rerun idempotence, hard-cap pending protection, and v4→v5 preservation. The Android fixture was compiled but not executed on the protected tablet.

Final commands and outcomes:

- `python3 scripts/check_wavelog_upstream.py`: `NO CHANGE` after transient HTTP 504 retry.
- `python3 scripts/check_openhamclock_upstream.py`: exit 2 / `REVIEW_REQUIRED`; stable/release/package/licence unchanged, already-reviewed preview-only satellite derivation/test change.
- `./gradlew :app:testDebugUnitTest`: 368 tests, 0 failures/errors/skips.
- `./gradlew :app:assembleDebug`: pass.
- `./gradlew :app:bundleDebug`: pass.
- `./gradlew :app:compileDebugAndroidTestSources`: pass.
- package-size and ITU/P.533 audit: pass.
- `git diff --check` and conflict-marker scan: pass.
- Shared-core CTest: not run because shared/native source did not change.

## Thirty-day disposable profile

A deterministic temporary SQLite database exercised 30 active days, all 16 bands, 30/60/120-minute windows, 15-minute persistence, periodic verification and daily compaction. It was deleted after measurement.

| Measure | Result |
|---|---:|
| Evidence rows | 138,240 |
| Peak prediction rows | 69,344 |
| Final prediction rows | 64,736 |
| Final pending rows | 224 |
| Calibration rows | 60 |
| SQLite size | 43,061,248 bytes |
| Median 48-forecast recomputation | 469.96 ms |
| Verification-cycle p95 | 0.24 ms |

These are host wall-clock observations, not tablet guarantees. The profile remains below the 50 MB soft database target and below the 100,000-row hard prediction cap.

## Artifacts and external limits

- APK: `android/app/build/outputs/apk/debug/app-debug.apk`; 114,649,022 bytes; SHA-256 `7402417fe8533b93daf67b714bf22279ca3367986ab8340a7479dcd6d8a1abe1`.
- AAB: `android/app/build/outputs/bundle/debug/app-debug.aab`; 51,623,548 bytes; SHA-256 `7f56e9416ea2a54ab00c62f3ac1d3637c46e53bfcb68c6cf6778790dfe8b1376`.

No APK was installed. Source/build evidence does not prove protected-tablet migration execution, physical UI, live provider/RF behavior, authenticated services, CAT state, PTT/TUNE or any real transmission. Final feature/main remote equality and clean-worktree proof are recorded in the handoff after the documentation commit and authorized pushes.
