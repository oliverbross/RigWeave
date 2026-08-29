# Android 1.0 final consolidation

## Candidate status

The final candidate starts from Android hardening SHA `cf9e78d010ee673baf4f5aae44a422bba040c6ca` on `integration/android-v1-final`. The accepted tablet sweep is already contained. The exact final SHA is not frozen until source, documentation, local regression, package, performance, watcher, and workflow-definition gates pass.

## Consolidated source

- Android version remains `1.0.0` / code `40`.
- Every accepted tablet-sweep fix is retained as `ALREADY_PRESENT_EQUIVALENT`.
- Grouped Hamlib selection, real Rotator configuration, consolidated Contest/Digi/DX Chaser Settings, adaptive Settings, database/index optimization, lifecycle/concurrency hardening, R8 shrinking, and accessibility fixes are retained.
- Linux workflows install the Secret Service development contract explicitly.
- SDRoxide v1.5.4 is reviewed at immutable commit/tree with unchanged repository licence; no upstream source or package is imported.
- The watcher pin, provenance ledger, and focused fail-closed tests are updated. The final hosted watcher may not hide exit 2 at job level.
- `PRODUCT.md` identifies Android 1.0.0 while preserving the suite's existing RC release boundary.

## Baseline package and performance contract

| Measure | Hardening baseline | Final requirement |
|---|---:|---:|
| arm64 APK | 35,461,691 bytes | no more than 39,007,860 bytes without measured justification |
| four-ABI AAB | 55,125,572 bytes | no more than 60,000,000 bytes |
| uncompressed DEX | 8,095,840 bytes | retain the hardening reduction |
| callsign lookup median | 1.25 ms | no more than 5 ms |
| worked-log median | 62.98 ms | no more than 100 ms |

The reproducible host benchmark on a private SQLite backup preserved 67,223 canonical and 67,223 projection rows. With the Android 1.0 migration applied only to the copy, 75 measured iterations after 10 warmups produced: callsign status for 25 keys 1.389 ms median / 14.726 ms p95, worked-log projection 68.133 / 268.999 ms, Logbook projection page 0.161 / 0.178 ms, and the Log Intelligence aggregate 227.829 / 262.442 ms. The two hardening medians remain within their 5 ms and 100 ms limits. This is host SQL evidence, not device rendering evidence; the retained JSON records query plans and emits no row values.

Android completed 762 JVM tests across 103 suites with zero failures, lint, APK/AAB and instrumentation-source packaging. Native normal, ASan, and UBSan builds each passed 8/8 CTests; the complete Rust workspace and both Apple build gates passed. Final artifact hashes, exact-SHA workflow jobs, and protected-device results are recorded only after those later gates run.

## Evidence boundaries

Source, local build, hosted workflow, protected data/install, unlocked visual, device performance, soak, authenticated service, audio, CAT/PTT/TUNE, RF, and rotator movement are separate layers. This task performs no CAT, PTT, TUNE, RF transmission, live TCI hardware connection, audio-quality claim, or rotator movement.

## Promotion boundary

`main` remains `8c085e979166d083283177d731a662a5424c7478` until the exact branch SHA equals the installed embedded SHA and hosted SHA, all mandatory jobs are green, and the complete physical tablet gate passes. `v0.1.0-rc.1` and its published release remain unchanged; this task does not create or publish `v1.0.0`.
