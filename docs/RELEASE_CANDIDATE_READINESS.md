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

- Universal debug APK: 124,629,714 bytes; SHA-256 `8c99b803efd4669e8611b5e86ac002b870f6d9906cd3ad1ed1207a1e23b86e58`.
- Debug AAB: 52,425,227 bytes; SHA-256 `7e1ad57078f5d0031d99a2d2cabbc05d9ba72e66504f43b25136b1538f7b9404`.
- Package audit: P.533 payload scan PASS for both archives.

These are unsigned/debug host outputs and were not installed or distributed.
