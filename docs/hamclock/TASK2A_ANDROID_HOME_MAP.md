# Task 2A Android Home and map completion

- Start SHA: `b84301db2109e6ed66e6a5c5019369b61e00767e`
- Final implementation SHA: `4df4f43d818d7495e0bb758e351a257868909fc2`
- Registry: one typed module registry drives defaults, migration completion, Layout UI, wide/compact rendering, deep links and unavailable handling; one typed layer registry drives map settings, source/layer IDs, bounds, selection and Map Data.
- Layout/settings: visibility, order, column, row span, column span, collapse, per-module/reset-all, density, UTC/local/both, 12/24-hour, metric/imperial, low-data and immersive modes are active. Profiles support create, apply, overwrite, rename, delete, clear-active, JSON import and JSON export.
- Map: one lifecycle-managed MapLibre view uses stable bounded GeoJSON sources. Camera/follow state persists with 600 ms debounce; manual pan disables follow; reset returns to DE. DARK/LIGHT use attributed CARTO/OpenStreetMap tiles with MapLibre attribution and logo enabled.
- Active layers: DE, DX spots, DX paths, selected target, PSK Reporter, portable, satellites, recent QSO projection, grayline/night, sun, moon, Maidenhead grid and lightning.
- Explicitly unavailable: RBN, expanded WSPR, IBP, aurora, global MUF, propagation heatmap, weather radar and WWBOTA. Satellite and terrain basemaps are unavailable until lawful configured tile sources exist.
- Low-data: Map Data consumes the same bounded snapshot, layer visibility, source labels, unavailable reasons and workspace actions without constructing MapLibre or requesting tiles.
- Header/target: one-second state is isolated to the header; display preferences control clocks and weather units; CAT/SAFE, SFI/A/Kp/SSN-unavailable, app/OHC/watcher state are visible. Manual targets use callbook, grid and CTY resolution, persist source/lock/clear, and feed selected-path and propagation state.
- Safety/performance: map actions only deep-link; none sends CAT. Home never materialises the full log, uses a bounded eight-column `qso_projection` query, caps every map layer, splits dateline paths and avoids map rebuilds on clock ticks.
- Validation: `cd android && ./gradlew testDebugUnitTest` passed (249 tests); `./gradlew assembleDebug` passed. A first test invocation exposed the required Material 3 bottom-sheet opt-in, and a later truth-audit run exposed stale Task 1 expectations; both were corrected before the recorded passing run.
- APK: `android/app/build/outputs/apk/debug/app-debug.apk`; 109,487,038 bytes; SHA-256 `5935978d1fe663ffc1f9180ddca8915a46e86eb0a1a12abadfc6adddb8d06af6`.
- External evidence: physical Android interaction, tile availability, gesture acceptance and live provider rendering require attached-device/operator evidence and are not inferred from JVM/build results.
- Repository: branch `feature/openhamclock-parity-v1`; source-complete commit above followed by the documentation commit. Clean status and remote equality are verified after push and reported with this record.
