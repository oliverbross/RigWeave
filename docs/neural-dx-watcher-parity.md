# Neural DX Watcher behavioural coverage contract

> **Status and coverage.** This is an active Android behavioural coverage/design contract, not a claim of exact, complete, licensed, or Apple parity and not proof of authenticated live success for every provider. Android source under NeuralDxController.kt, NeuralDxScreen.kt, and NeuralDxMap.kt implements the listed local stores, views, maps, and provider adapters to differing depths. Shared CTY/DX/operator-intelligence primitives live in the C++ core. Apple currently exposes a smaller DX/cluster/solar feature set and does not implement these Android pages. Provider credentials, terms, network availability, and physical-device proof remain separate gates; see [NEURAL_DX_WATCHER_INTEGRATION.md](NEURAL_DX_WATCHER_INTEGRATION.md) and [phase-0/ACTUAL_FEATURE_INVENTORY.md](phase-0/ACTUAL_FEATURE_INVENTORY.md).

Behavioural reference reviewed: `F1SMV/Neural-DX-Watcher` v12.1. The available upstream
commit and approved behavioural baseline is `fe3cba8ed9c0502f5dabdb2f64ebd990de986559`
(2026-08-16). The original remediation brief contained a non-resolving transcription/reference
error. The upstream licence/permission remains unresolved; the baseline does not establish
exact parity or permission. See the integration record.

RigWeave implements the project as an Android-native DX workspace inside the
existing Flightline interface. Flask, nginx, browser themes, and local web API
tokens are deployment mechanics rather than operator features and are replaced
by in-process controllers, encrypted preferences, SQLite, and Compose UI.

## Operator surfaces

- **Cockpit:** live DX feed; HF through 3 cm observation-band, mode, watchlist, and new-DXCC
  filters; active-band rates and surge warnings; ranked opportunities; solar
  SFI/A/Kp; watchlist tracking and expiry; manual cluster spot; My Signal from
  PSK Reporter; deterministic current opportunities with explicit priority and evidence support; LoTW/local
  worked-state opportunities; and 6 m activity heatmap.
- **Map:** individual geolocated spots; time, count, band, and mode filters;
  station detail and tune action; recenter; and Who Hears Me receiver view with
  great-circle reach.
- **AI Insight:** live tactical DX briefing, solar and activity summary,
  DXCC/continent/band/mode log analysis, missing-entity opportunities,
  watchlist recommendations, generated report status, and explicit on-demand
  refresh. The deterministic local report always works; an optional configured
  Perplexity key adds the upstream AI brief.
- **World:** band and 15–360 minute window controls; anomaly-only and grey-line
  overlays; observed region cells, expected baseline, anomaly ratio,
  confidence/sample count, and an explanation/reality-check view.
- **Briefing:** 12-hour cached DX-World, DXNews, NG3K ADXO, and QO-100 sources;
  per-source status and items; operator reordering; manual refresh; DX mode;
  callsign extraction; and one-tap watchlist addition.
- **Satellites:** CelesTrak OMM/TLE refresh and cache; followed-satellite list;
  searchable amateur catalogue; current latitude/longitude/altitude,
  azimuth/elevation/range/visibility and footprint; 4/12/24-hour pass list with
  AOS/TCA/LOS and maximum elevation; and cached SatNOGS uplink/downlink/mode
  details.
- **Weather Radio:** global HF/VHF heuristic synthesis with honesty labels;
  Open-Meteo local HF and VHF/UHF conditions; pressure trend, humidity, wind,
  precipitation, CAPE, 300 hPa wind, and tropo/ducting heuristic; WSPR.live HF,
  VHF and 2 m confirmation; QRN/noise correlation; regional lightning state;
  Quick VOACAP path reliability; 24-hour band activity; monthly
  dl0tud beacon-reference refresh and nearby reception list; and actionable
  weather/propagation alerts.

## Shared behavior

- Reuse RigWeave's single primary-plus-two-fallback cluster connection and
  CTY.DAT resolver; never open a competing cluster stream.
- Keep bounded live state and a durable indexed spot journal. Network caches
  survive restarts and expose age, source, refresh, loading, unavailable, and
  stale states without fabricating values.
- Use the configured local log or selected Wavelog station for worked status.
  A QSO is confirmed only by paper QSL or LoTW, as explicitly required for
  RigWeave.
- Watchlist/New DXCC/6 m alerts use Android notifications plus optional ntfy,
  cooldowns, and foreground-presence suppression.
- All callsign, spot, current-opportunity, map, and satellite actions remain receive-only
  until the operator explicitly confirms an existing RigWeave CAT tune or
  cluster-post action.
- Shared cluster observation uses the fixed ADIF-compatible order `160m`, `80m`,
  `60m`, `40m`, `30m`, `20m`, `17m`, `15m`, `12m`, `10m`, `6m`, `4m`, `2m`,
  `70cm`, `23cm`, `3cm`; unsupported frequency gaps are rejected.
- QO-100 observations retain canonical `3cm` identity, with an optional secondary
  display annotation. Neural DX direct CAT tuning remains disabled above 6 m;
  higher-band observations, detail, activity, history, and scoring remain available.
- Android and iOS consume the same shared observation snapshot and canonical
  worked-band mappings. The Android seven-page workspace is not ported to iOS,
  and desktop Neural DX remains unimplemented.
- QRZ/HamQTH/CTY enrichment remains QRZ.com first, then HamQTH, then CTY.DAT.
- The earlier explicit **no WSJT-X yet** constraint remains in force. My Signal
  and weather correlation use PSK Reporter/WSPR sources; no UDP listener is
  introduced.

## Historical target expectations

The following points are targets from the earlier coverage programme, not evidence that every item is complete:

- Every surface above has real data, loading, empty, stale, error, refresh, and
  offline-cache states.
- Filters and actions are covered by unit tests; persistence and migrations are
  additive; network parsing is bounded; list/table rendering is paged or lazy.
- Android debug build and tests pass, and the final APK is installed and
  exercised on the connected Lenovo tablet. No iOS build is run.

For the current-opportunities closure, Android JVM tests and both debug APK assemblies pass. The focused disposable-database instrumentation test compiles but was not installed on the connected operator tablet; device execution awaits a safe disposable target. This build evidence does not prove physical UI, live-provider, authenticated-service, CAT, radio, or RF behaviour.
