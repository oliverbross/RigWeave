# Android 1.0 protected-tablet acceptance

## Current state

The Lenovo TB373FU is not visible through USB ADB or already-authorized Wi-Fi/mDNS after a clean ADB daemon restart. No package query, backup, install, screenshot, device benchmark, or main promotion is claimed while the tablet is absent.

## Hard safety gates

1. Select only the TB373FU/HA248BS3 endpoint after model, serial, and package identity agree.
2. Require `pm path app.rigweave.mobile` before any install action.
3. Capture a fresh private-data archive and hash manifest before installation.
4. Verify package ID, version code, arm64 ABI, embedded final Git SHA, and installed/candidate signer equality.
5. Stop on missing package, signer mismatch, downgrade risk, schema incompatibility, or preservation failure.
6. Install only with `adb install -r`; never uninstall or clear data.

## Required preservation record

- UID and first-install time.
- installed and candidate signer digest.
- installed APK hash and pulled-back post-install hash.
- durable non-cache file hashes and credential-bearing preference-file hashes without values.
- all database hashes, `quick_check`, schema versions, canonical QSO count, projection count, missing/orphan projection counts.
- selected station/log and configured feature metadata without credentials or private message bodies.

Expected pre-install identity remains package `app.rigweave.mobile`, UID `10352`, signer `de2e3f61831624d755846e788c109f08f4bdd0aac4ac9cd2a925aa77c4bb0ca8`, and 67,223 canonical/projection rows. These are prior evidence, not current proof; they must be remeasured.

## Physical matrix

The final exact-SHA candidate requires at least 30 lossless screenshots and matching UI hierarchies across Home, Spectrum, RF Map, RF Globe, Radio/catalog, Rotator, Settings/Screens hide-and-restore, Contest, Digi, DX Chaser, Band Maps, Logbook, Log Intelligence, Remote Station, TCI controls, Health, and About.

Acceptance must recheck the retained sweep behaviors, hardening settings/catalog/rotator behavior, accessibility/reachability, and performance-sensitive Logbook/Intelligence/search paths. No connected radio or rotator action is permitted.

## Performance and soak

Record median, p95, and maximum for 20 callsign lookups plus 10 each of worked-log, Logbook, Intelligence, Settings, and radio-catalog opens. Then run a 45-minute unlocked foreground soak sampled at 0/15/30/45 minutes, followed by 10 cold relaunches and 10 warm/background cycles.

The pre-device host SQL profile on a migrated private copy retained 67,223/67,223 canonical/projection rows and measured callsign status at 1.389 ms median, worked-log projection at 68.133 ms, Logbook paging at 0.161 ms, and the Intelligence aggregate at 227.829 ms. It is a deterministic regression aid only. Application startup and screen-open/render measurements remain device-only and pending.

The fresh crash buffer and time-bounded logs must contain no FATAL EXCEPTION, ANR, OOM, SQLite, native abort/tombstone, Keystore, MapLibre, WebView, or repeated-provider-loop failure. Radio, rotator, TX, scanner, recording, and remote listeners must restore inert.

## Evidence boundary

Installation or deterministic diagnostics cannot establish authenticated-service, audio, CAT/PTT/TUNE, RF, on-air, or rotator-movement acceptance. Those layers remain explicitly unperformed.
