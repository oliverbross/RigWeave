# Task 1 watcher safety closure

## Repository boundary

- Branch: feature/openhamclock-parity-v1
- Required starting SHA: 428a784867a322a2addccbe660e92e9847c171fa
- Final audited implementation SHA: fc56a5001146e7be60527286325409c7a9cd20ca
- The implementation SHA preserves the two pre-existing Task 1 commits. This closure note is the second and final bounded commit.

## Watcher contract and current result

The watcher compares the reviewed stable branch, preview branch, and latest release independently. It validates and peels the latest tag to a commit, compares package version and licence digest at that release commit when available, and records bounded commit/path inventories plus explicit truncation. Preview classification examines commit subjects/bodies and paths for security, provider-contract, and propagation-algorithm triggers.

Exit 0 means no review is required, exit 2 means review is required, and exit 1 means the comparison failed. The workflow continues past the comparison step only to upload reports, creates or updates one fixed-title issue when attention is required, and then fails the job for exit 1 or 2.

The final read-only local comparison returned exit 0 / NO_REVIEW:

- latest release v26.5.0 remained at cc2415e70cce5f9a583fa32efaf1c66792d030df with package 26.5.0 and the reviewed MIT licence digest;
- stable main remained at d4a50eaaa61d3432a1de5f80cbe61790739930a5;
- preview Staging remained at 36e5c1262dfde2057b2b4e6483be8c2215c70ad4;
- stable and preview remain diverged, with no changed preview inventory to classify.

## Provider request correction

HamClockInFlightCoalescer now completes the shared future before removing the exact active entry, removes rejected submissions without stranding a key, preserves execution causes, and restores interrupt status. Independent keys remain independent and an interrupted observer does not cancel shared work.

SolarCelestialProvider now coalesces the coordinate-independent NOAA GOES X-ray request under one resource key. Each caller still computes its own coordinate-specific sun and moon snapshot after receiving the shared network result.

## Validation and evidence

Final successful validation:

- python3 -m unittest scripts/test_check_openhamclock_upstream.py — 7 tests, OK;
- cd android && ./gradlew testDebugUnitTest — BUILD SUCCESSFUL;
- cd android && ./gradlew assembleDebug — BUILD SUCCESSFUL.

The isolated worktree initially lacked Android SDK environment configuration, so the first Gradle test configuration attempt stopped before tests ran. The first assemble attempt then exposed an obsolete Rust compiler selection. The successful runs used the installed Android SDK and explicit rustup cargo/rustc paths; no machine-local configuration file was added.

APK:

- Path: android/app/build/outputs/apk/debug/app-debug.apk
- Size: 109,471,121 bytes
- SHA-256: ef99e9d336453599aa0d7ff88765e77b81c4862a6e17ca8c19871b32ccc4e12c

The bounded source recheck confirmed the 36-panel and 24-layer parity inventories, separate stable/preview/release pins, truthful settings availability with active rowSpan and map visibility consumption, strict propagation parsing with a 1 MB limit, MIT attribution, native-client boundaries, and no imported React/Node/runtime upstream source.

Build, unit-test, source-scan, and GitHub API evidence do not establish physical-device UI, audio, RF, external-provider availability, workflow execution on GitHub, or Apple-platform behavior. No device, deployment, release, PR, merge, or Apple work was performed.

The owner archive had already been deleted by the prior run and was not touched by this closure.
