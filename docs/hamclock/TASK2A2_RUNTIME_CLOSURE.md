# Task 2A2 Android runtime integration closure

Date: 2026-08-20

Verdict: PASS at the Android source, JVM-test and debug-build boundaries.

## Baseline and implementation

- Required start: `bdb2e1691d088ba07796957ba1012835ee6d695b`.
- Source-complete implementation: `19b5c213b3cbfec44dea6e30306c6cc5e345b944` (`fix(home): close runtime integration gaps`).
- Branch: `feature/openhamclock-parity-v1`.
- This completion note is the subsequent documentation commit; the final pushed branch SHA is reported in Git and the task handoff because a commit cannot embed its own hash.

## Runtime closure

- General Radio/Preset CAT no longer passes through Home review. Elecraft receives the original complete command, including compound `FA`/`MD`/`BW`; Flex retains supported receive frequency/mode semantics.
- Home favourite-band and Home-launched DX actions use one explicit receive-only review. Operations satellite downlink preview uses the same scoped callback. Cancel dispatches nothing; confirmation cannot key PTT, start TUNE, change TX frequency, arm transmission or retry.
- `SatelliteOperationsController` owns Home satellite positions. A visibility/foreground-scoped 45-second local SGP4 lifecycle uses validated elements, station/Wavelog Home observer, favourites plus selected satellite, eight-row fallback, 40-position cap, mutex serialization, generation guards and clean cancellation.
- `NeuralDxRefreshScope.HOME` omits the legacy satellite fetch/ticker. `FULL_DX` retains that legacy path for the existing Neural DX satellite page; Home neither starts nor renders it.
- Exact map routing is `DX_SPOT`, `PSK_REPORT`, `PORTABLE`, `SATELLITE`, `QSO`, `TARGET`, `WEATHER` or `NONE`. PSK reports use callsign/epoch/frequency/locator identity and select the exact My Signal row; they never call `FeatureController.requestSpot`.
- Exact DX detail offers receive review, Advanced Logbook callsign history, add/remove watchlist and close. Portable preserves spot ID, Satellite preserves NORAD, and QSO preserves projection QSO ID. Map Data invokes the same request types with specific action labels.
- Every registry layer has sanitized source truth. Visible header totals distinguish current, degraded, empty and unavailable; Portable detail exposes each POTA/SOTA/WWFF state; satellite status includes element state and position age; QSO status includes the bounded projection revision.
- DX fill/path colour remains the shared band colour. Watchlist is a separate GeoJSON property rendered as a ring and a low-data star label.
- A late current-generation OpenFreeMap callback clears the eight-second warning and fallback, installs layers once and resumes source updates. Obsolete generations remain ignored. DARK remains local/no-network; LIGHT remains attributed OpenFreeMap Liberty.
- Home metric/imperial selection covers weather, DX target, propagation, PSK, satellite altitude and lightning distance, using metres/feet for sub-unit values. Density remains truthfully `PARTIAL` and is labelled Layout density because typography/control scaling is not yet complete.
- SFI/A/Kp publish before optional SSN. SSN failure retains the last valid value/month and has a separate sanitized error. Parsing selects the greatest valid bounded non-future `YearMonth`, independent of array order.

## Scale recheck

- No interactive `QsoDatabase.all()` or canonical QSO JSON decode.
- Recent Home QSO query remains `recentHamClockProjection(120)`.
- Provider/network work remains off-main.
- One Home satellite authority; no overlapping Home propagation jobs.
- Map view is not recreated by clocks; unchanged GeoJSON fingerprints are not rewritten.
- Diagnostics contain no raw payloads, credentials, comments or QSO data.
- No automatic transmit-capable behavior was added.

## Validation actually run

With Android SDK variables and rustup-managed Cargo configured:

- `cd android && ./gradlew testDebugUnitTest`: PASS, 262 tests, 0 failures, 0 errors, 0 skipped.
- `cd android && ./gradlew assembleDebug`: PASS.
- `git diff --check`: PASS.
- Initial validation attempts stopped at compile diagnostics (missing Compose effect import, enum visibility, then an invalid top-level `onDispose` import). After those declarations were corrected, one PSK test reached an Android `Color` JVM stub; it was moved to the pure PSK identity helper. Only the final green results above are release evidence.

APK:

- Path: `android/app/build/outputs/apk/debug/app-debug.apk`
- Size: `113450647` bytes
- SHA-256: `1278076d4180df036c55ffc78e24c4bc9047dc168a55114772aad974dd4c940a`

## Evidence boundary

No instrumentation, screenshots, emulator/device installation, physical tablet interaction, physical CAT/radio operation, RF transmission, or authenticated live-provider session was run. The debug APK and deterministic offline JVM coverage prove source/build behavior only; physical interaction and current external-provider behavior remain external evidence.
