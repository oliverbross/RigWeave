# Release Candidate Readiness

## Mandatory automated gates

- Watcher review for Wavelog, OpenHamClock and Nexus.
- Python contract/provenance/privacy scan.
- Rust suites and native CMake/CTest.
- Android JVM tests, lint, APK, AAB and Android-test compilation.
- Android package audit against the frozen-baseline evidence.
- Unsigned Apple generic-device build.
- Fresh/current/legacy schema migration matrix and soak exercises.

## Current upstream disposition

- Wavelog 3.1.0 at `af3256140bd05403b7c4a421746c2ea653a4f04f`: no change.
- OpenHamClock stable `d4a50eaaa61d3432a1de5f80cbe61790739930a5`: unchanged. Preview `99913f2df574b8588ddaff703581b8f341f46761` contains an already-reviewed satellite display/test delta and is not absorbed.
- Nexus current `8908d1b…` (1.7.6): reviewed for product lessons only. Excluded modem/radio/desktop/notch/compressor/WinKeyer code is not imported.

The workflow's watcher job is advisory because upstream movement requires human semantic review. Release readiness cannot be inferred from that job alone.

## External boundary

No device install, credential use, authenticated-service mutation, signing, deployment, store action, CAT/PTT/TUNE or RF transmission is part of this candidate.

## Disposable scale profile (2026-08-22)

The deterministic host profile passed and deleted its temporary databases. Observed on this host: 100k-QSO insert 382.31 ms, keyset page 0.19 ms, aggregate 83.19 ms, streamed export 161.24 ms; 30k-message all-groups FTS 1.54 ms. Database sizes were 9,605,120 bytes (logbook), 1,085,440 (Neural), 602,112 (Digi), and 2,723,840 (Groups.io). These are host simulation timings, not device performance claims.

## Final host artifacts

- Universal debug APK: 124,629,714 bytes; SHA-256 `d3219965ee572929cb88426200fd4b94c0bebb5b952f03e41a9de6d3213c2459`.
- Debug AAB: 52,425,282 bytes; SHA-256 `2f8d2a438b43adc6b973b0167d474b5f94409bac8abe0a2fb24e4bdab79c6ee4`.
- Package audit: P.533 payload scan PASS for both archives.

These are unsigned/debug host outputs and were not installed or distributed.
