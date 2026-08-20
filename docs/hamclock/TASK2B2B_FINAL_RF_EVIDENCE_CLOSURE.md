# Task 2B2B Final RF Evidence Closure

## Scope and baseline

- Required starting commit: `c524aab2260485cc3196e875208fad4282eab018`
- Source-complete milestone: `233ecb0fdf15b633267044d3191b5d5fe11247c0`
- Branch: `feature/openhamclock-parity-v1`
- Upstream watcher: passed with no drift reported
- This closure is Android source, automated-test, build, and documentation evidence. It is not physical-device, live-provider, authenticated-service, RF-reception, or propagation evidence.

## Closure gates

### Gate A — RBN semantics, geometry, expiry, and source truth

- WHO HEARS ME is keyed to the current station callsign.
- SKIMMER VIEW normalizes numbered skimmer suffixes to their base identity.
- WATCHLIST comparison uses normalized amateur callsign identities.
- Map points represent skimmer receivers; paths run from the transmitting DX endpoint to the skimmer.
- Endpoint resolution is bounded and ordered: stream grid, current-station grid, cached callbook grid, approximate CTY location, then unavailable.
- A foreground maintenance cadence expires quiet-feed rows off the main thread and publishes explicit source state, counts, age, endpoint, and sanitized failure text.

### Gate B — PSK Reporter and WSPR

- Station-grid changes reproject retained PSK Reporter and WSPR observations locally without issuing HTTP requests.
- Mixed provider health reduces to an explicit DEGRADED state.
- Personal WSPR remains the implemented PSK Reporter-backed surface; unsupported regional aggregation remains visibly unavailable.

### Gate C — DX News and NG3K

- DX News refresh is app-scoped and uses the shared repository/controller path.
- The DX surface consumes the same current snapshot rather than owning a second polling loop.

### Gate D — IBP

- The canonical 18-beacon ordered vector, labels, grids, and recomputed hash are tested.
- Details include distance, bearing, current/next schedule, observed cluster/RBN evidence when present, and explicit absence when not observed.

### Gate E — Band Health and shared evidence

- Home, DX, and Progress consume one station-scoped Band Health snapshot.
- Contributor IDs are explicit and confidence falls for stale, degraded, or missing sources.
- Historical comparison uses a compact indexed projection aggregate bounded by station, band, mode, one year, and comparable UTC window; canonical/full-log scans are not used.
- Progress labels the surface as operational live evidence, not propagation forecast or award credit.

### Gate F — lifecycle

- Long-lived provider work is gated by foreground state.
- Existing controller-owned schedulers are reused; no per-card polling loop was added.

### Gate G — documentation and provenance

- Parity, provider, decision, scale/stability, truth-table, prior-closure, and upstream ownership records were reconciled with the implementation.
- Inherited commits:
  - `19f19b3fbdefc2226c1ce906615fda8b4c1f64bd` — Android startup database configuration
  - `a59d75d6ea46b3ecb4ddfc9b8b03a3e0db1d646b` — RF evidence correctness closure
  - `c2a8c9e01cf134d9dbfee45c297385a258509a18` — Home map/layout closure
  - `8b026fbe8d744b8e4aec9eb414d6a035a9c01255` — corresponding documentation
  - `c524aab2260485cc3196e875208fad4282eab018` — final inherited Home corrections
- The final documentation commit and pushed branch tip are reported in the delivery record because a commit cannot contain its own hash.

### Gate H — scale, privacy, and radio safety

- Evidence collections and historical queries are bounded.
- Credential values are not logged, copied into artifacts, or included in tests.
- No uninstall, data clear, private-data inspection, or device install was performed.
- No new CAT/tuning authority or direct radio-control path was added.

## Validation evidence

- `python3 scripts/check_openhamclock_upstream.py`: passed
- `./gradlew testDebugUnitTest assembleDebug`: BUILD SUCCESSFUL
- JVM tests: 294 passed, 0 failed, 0 errors, 0 skipped
- `git diff --check`: passed for the source milestone
- Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- APK size: 113,630,228 bytes
- APK SHA-256: `c4c3cd529255c3ac01913e359f7825991e1de8b6a895137eb70991b761cf7075`

## External limitations

- Connected Android instrumentation was not run because this task did not authorize an installation or other tablet state change.
- Physical layout, physical-device startup, live provider acceptance, authenticated Wavelog/QRZ behavior, and RF reception remain external evidence and are not inferred from the successful source build.

## Result

PASS for the Task 2B2B source-closure gates, subject to the explicitly separated external evidence above.
