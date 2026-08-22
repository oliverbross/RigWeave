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
- Nexus current `f0869a11…` (1.7.6): watcher review remains required for digital/audio/UI changes. Excluded modem/radio/desktop/notch/compressor/WinKeyer code is not imported.

The workflow's watcher job is advisory because upstream movement requires human semantic review. Release readiness cannot be inferred from that job alone.

## External boundary

No device install, credential use, authenticated-service mutation, signing, deployment, store action, CAT/PTT/TUNE or RF transmission is part of this candidate.

## Keyer, Contest/N1MM and DX Chaser integration candidate

The isolated `integration/keyer-contest-dxchaser-v1` branch preserves all three frozen histories and
adds production navigation, controller lifecycle, typed Contest-to-Keyer and canonical-QSO adapters,
default-off N1MM runtime, exact local-decode Chaser-to-Digi preparation, canonical QSO feedback,
configuration/Health/privacy integration and the read-only Band Maps contract. The local source/build
gates and artifact audit below pass. No physical or live evidence is implied.

Local integration validation on 2026-08-22:

- Frozen base and three exact-source ancestry checks: PASS; three exact non-fast-forward merges, no textual conflicts.
- Watchers: Wavelog exit 0/no change; MSHV exit 0 with commit/tree/licence digests unchanged; OpenHamClock and Nexus exit 2/review required, read-only, with no upstream code absorbed.
- Rust `cargo test --locked`: PASS, 97 passed and one intentionally ignored.
- Required Debug CMake build and CTest: PASS, 2/2 targets.
- Android unit tests, APK, AAB, instrumentation-source compilation and lint: PASS. Instrumentation sources compiled but were not executed on a device/emulator.
- Lint: 0 errors, 170 warnings and 37 hints; warnings/hints are non-blocking.
- Package audit and repository authority/privacy scans: PASS.

## Disposable scale profile (2026-08-22)

The deterministic host profile passed and deleted its temporary databases. Observed on this host: 100k-QSO insert 382.31 ms, keyset page 0.19 ms, aggregate 83.19 ms, streamed export 161.24 ms; 30k-message all-groups FTS 1.54 ms. Database sizes were 9,605,120 bytes (logbook), 1,085,440 (Neural), 602,112 (Digi), and 2,723,840 (Groups.io). These are host simulation timings, not device performance claims.

## Final host artifacts

- Universal debug APK: 111,637,568 bytes; SHA-256 `7f03589575cc88849fd923f1381fc149c85cdd27f21f0ee7873030c67c53a25e`.
- Debug AAB: 52,909,771 bytes; SHA-256 `94718a085e1de6ee2bc52dcf466ca7819bad0eff85673a8f9176657fedf6d0f8`.
- Package audit: P.533 payload scan PASS for both archives.

These are unsigned/debug host outputs and were not installed or distributed.
