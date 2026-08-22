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

- Universal debug APK: 124,629,714 bytes; SHA-256 `2ae56a5c114427eebe8b710d2570ec896824e1f7264b2637705f650f979b8e7c`.
- Debug AAB: 52,426,039 bytes; SHA-256 `8e15d0e1354f7f858f068fe0d7dd9bcdfd3dcbbe04543d9fe9ec5915ca183d6d`.
- Package audit: P.533 payload scan PASS for both archives.

These are unsigned/debug host outputs and were not installed or distributed.
