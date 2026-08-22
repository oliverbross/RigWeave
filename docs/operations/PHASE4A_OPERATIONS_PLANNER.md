# Phase 4A — Operations Planner

## Sweep 1 catalog authority

Activation Planner now queries independent bounded POTA and SOTA catalogue snapshots off-main, deduplicates exact references, applies inclusive great-circle radius without country restriction, and sorts by distance/name/reference. Provider state, catalogue/nearby counts, attribution and invalid-coordinate counts are visible. WWFF is not substituted with live agenda spots: because no stable licensed structured full catalogue contract is available, both Operations and Portable state PROVIDER UNAVAILABLE explicitly.

Verdict: PASS

Source-complete Android features:
- Tablet-rail Operations destination plus compact Home, DX and Portable entry points.
- DX Calendar groups active, soon, upcoming and recently ended entries; search, local call/DXCC history,
  Progress needs, watchlist, copy/source/live-spot actions and exact Advanced Logbook drill-through.
- Contest Calendar groups local/UTC schedules; mode/search filters, deterministic ADIF IDs where known,
  local QSO counts, rules/copy/ICS actions, Fast Entry context and exact Advanced Logbook drill-through.
- Activation Planner supports grid input, map tap, explicit current-location permission, grid-cell outline,
  distance/bearing, honest overlay availability, nearby POTA/SOTA/WWFF references and durable plans.
- Plans support create, edit, duplicate, confirmed delete, copy, ICS share, next-plan summaries and safe
  POTA/Portable handoff. POTA details never start a session or pre-accept the boundary acknowledgement.

Providers/cache:
- NG3K ADXO HTML and WA7BNM RSS use the existing HTTPS, bounded, 30-minute shared providers.
- Validated last-good data survives refresh failure with CURRENT/STALE/OFFLINE CACHE/EMPTY/ERROR truth.
- POTA/SOTA downloaded catalogues and the existing WWFF spot/agenda cache supply nearby references.
- Provider details and pinned Wavelog review paths are in `PROVIDERS.md`; no Wavelog code/data was copied.

Integration:
- Home shows live Operations counts and next plan; DX rows gain calendar markers and calendar entry.
- Portable shows the next plan; Advanced Logbook receives deterministic callsign/contest filters.
- Calendar/planner actions do not perform radio control or transmit.

Validation performed:
- `./gradlew testDebugUnitTest` — PASS.
- Phase 4A targeted five-test class after the final integration correction — PASS.
- `./gradlew assembleDebug` — PASS; final incremental `packageDebug` — PASS.
- APK SHA-256: `c79da747f131b188ece91bde1c6062eec718fdcd0d3242e9168c635f9427b412`.

External evidence limitations:
- No physical Android interaction, live in-app provider refresh, GPS fix or share-target acceptance was run.
- CQ/ITU/state polygon datasets are not packaged; the UI reports them unavailable rather than inventing data.
- Apple builds and UI work were intentionally not performed.

Exact source commit: `4ab4f43d97f32ddc84c382176929b6b36476b6b7`.
