# Task 2A1 correctness closure

Reviewed 2026-08-20 on `feature/openhamclock-parity-v1`, starting from `7328ef6ebd9eb37b304fca551628c4ac56147409`.
Implementation commit: `8863ef55d933ff2853adbaa92c4ecfde548508b4`.

## Closure results

- Basemap: removed hard-coded CARTO calls. DARK is bundled/no-network. Optional LIGHT uses the documented OpenFreeMap Liberty public style over HTTPS with MapLibre attribution/logo enabled; no key, demo credential, offline pack or custom cache is committed. Low-data requests no tiles; style failure retains Map Data.
- Layout: a hidden Map remains hidden across wide, compact, low-data, profile and import paths. When absent, center modules use the available wide area. Known module positions/spans/collapse normalize to registry capabilities. The action is labelled `RESET PANELS` and preserves profiles.
- Lifecycle/camera: click handling reads current layer preferences. Only gestures schedule persistence; the delayed write merges into the latest preference and is rejected after a newer camera/profile. Programmatic follow/profile moves do not clear a profile. Style callbacks use a generation guard and unchanged GeoJSON source payloads are not rewritten.
- Identity/actions: GeoJSON preserves layer, feature/context ID, selection, callsign, frequency and mode. DX, Portable, NORAD, QSO and target drill-through open exact destination context. Target opens edit/lock/clear. Frequency changes use one receive-only review showing frequency, mode, source, reason and radio; cancel is available and confirmation never keys PTT or starts TUNE.
- Satellite/celestial: Home consumes only `SatelliteOperationsController.hamClockPositions`, calculated off-main by pinned `NativeSatellite` SGP4 from the controller's validated elements and observer grid. Stale cache truth is retained. The fabricated Moon antipode was removed; Moon phase remains available, while the map layer states why sublunar geometry is unavailable.
- Truth/UI: Minimal Home replaces the overstated immersive label and is `PARTIAL`. Density affects breakpoints, padding, inter-panel spacing and panel heights. Units propagate to weather plus map distances/altitudes. Header SSN uses latest valid monthly NOAA `observed_swpc_ssn` with app-private last-good storage, and upstream provenance is the reviewed version/SHA/date rather than watcher state.
- Source truth: Map Data shows typed source state, observation time/provenance and sanitized error detail. DX uses the established band palette. PSK parity uses upstream ID `psk-reporter`. The Maidenhead grid cap is the actual 32 generated lines.

## Validation boundary

JVM tests cover registry/render contracts, exact map identity, caps, Moon absence, band colour, unit formatting, stale camera rejection, NOAA SSN parsing, hidden-map persistence and legal layout normalization. Android build evidence proves compilation/packaging only. Physical gestures, visual attribution placement, live provider behavior, exact destination interaction and radio/device behavior require attached-device/operator evidence and are not inferred from builds.

## Validation evidence

- `cd android && ./gradlew testDebugUnitTest`: PASS, 254 tests, 0 failures, 0 errors, 0 skipped. The first closure run exposed one JVM-only Android graphics dependency and one stale immersive-truth expectation; both were corrected before the recorded final run.
- `cd android && ./gradlew assembleDebug`: PASS.
- APK: `android/app/build/outputs/apk/debug/app-debug.apk`; 113,437,904 bytes; SHA-256 `395af29a8d699da671c73ca8720d0bff4cfc61f24aeb1cd5907370ab751bb44e`.
- Static checks: `git diff --check` passed; hard-coded CARTO endpoint, runtime watcher-state, fabricated Moon-marker and `neuralDx.satellites` scans were clear.
- `docs/wavelog/archive.zip` is absent and untracked.
