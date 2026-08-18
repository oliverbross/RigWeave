# Android Portable Chase

## Purpose

Portable → Portable Chase is the Android hunter cockpit for POTA, SOTA, and WWFF activity. It keeps POTA's proven live/cache/offline and staged park-catalogue path, normalizes provider activity only at the list/ranking/map/logger boundary, and never claims official programme credit.

## Sources and cadence

- POTA spots: `https://api.pota.app/spot/activator`; foreground On Air refresh at most once per 60 seconds. The explicit offline park catalogue remains `https://pota.app/all_parks_ext.csv`.
- SOTA summits: `https://www.sotadata.org.uk/summitslist.csv`; explicit operator download into a staged app-private SQLite database, with header-by-name streaming import, validation, sampled reopen, SHA-256/HTTP metadata, and previous-database retention.
- SOTA live: unavailable in this build. On 18 August 2026 the formerly proposed API2 endpoint returned a deprecation record. Its replacement terms require an approved designated contact and prior approval for AI-generated clients. RigWeave therefore shows `SOTA UNAVAILABLE — API APPROVAL REQUIRED` and makes no live request to either endpoint.
- WWFF spots/agendas: `https://spots.wwff.co/static/spots.json` and `https://spots.wwff.co/static/agendas_active.json`; one foreground refresh per 60 seconds. Agendas only enrich matching current spot rows and never create frequency-less activity.

Each provider owns its status and bounded snapshot cache. A failed refresh retains the last valid normalized snapshot as cached/stale; malformed, empty, HTML, QRT, TEST, expired, or materially future activity is not ranked as live. Requests identify RigWeave, use bounded timeouts, at most one retry, conditional headers where available, and no overlapping provider refresh.

## Operator workflow

On Air provides All/POTA/SOTA/WWFF selection, band/mode/search filters, deterministic sorts, worked-state reasons, joined selection, and a Flightline list/map/detail cockpit on expanded tablets. Compact layouts preserve the same primary controls without adding a bottom-navigation item. MapLibre distinguishes programmes, clusters broad views, retains operator map position after gestures, and reports rows without coordinates.

Worked labels come only from the local/Wavelog-synchronised QSO database: POTA uses `potaRef`, SOTA uses `sotaRef`, and WWFF uses `wwffRef`. They describe local history, never official confirmation. Cross-program grouping requires exact normalized callsign and mode family, valid structured references, timestamps within three minutes, and frequency within 250 Hz for CW/digital or 1 kHz for voice modes.

Tune and Tune & Log are operator-confirmed receive-only actions. Selection never tunes. CAT must be live; the action sends VFO A and only an unambiguous receive-mode command, never TX, TUNE, PTT, or a macro. A grouped draft populates the other station's `potaRef`, `sotaRef`, and/or `wwffRef`; all three `my*` fields stay empty in Chase mode. The existing editable logger, callbook, local SQLite, and optional Wavelog flow remain authoritative.

## Places and licence boundaries

POTA Parks retains its existing offline download/search/Nearby experience. SOTA Summits supports explicit staged download, reference/name/association/region search, Nearby, validity, altitude, points, source freshness, and official browser handoff. WWFF Places searches only bounded recently seen Spotline data and links to the official Directory. The full WWFF Directory is not downloaded, bundled, cached, scraped, or reproduced without programme permission.

RigWeave is independent of POTA, SOTA, and WWFF and uses no programme logos.

## Validation and current limitation

- Focused JVM rules plus `./gradlew :app:testDebugUnitTest :app:assembleDebug` pass (103 tests, zero failures). The final debug APK is version `0.1.0` (`versionCode 1`), 88,113,807 bytes, SHA-256 `223fec13e278b211b802e9c4de4a2e333bbbf4bb1b810dc57c4746853a1da954`.
- The real WWFF spot and agenda shapes were verified on 18 August 2026. The old SOTA response shape was sanitized into `android/app/src/test/resources/sota/current-spots-sanitized.json`; it is a test fixture only, never production fallback data.
- Lenovo TB373FU: `adb install -r` passed with app data preserved. POTA loaded 56 usable spots and WWFF loaded 4 usable spots during the final run; their independent filters, WWFF detail/map data, CAT-offline zero-command state, and background/resume completed without an Android runtime crash.
- The official 2026-08-17 SOTA CSV imported 181,658 summits. Searches selected `VK3/VC-001` Mt Matlock and `OM/ZA-001` Bystrá. Nearby from manual grid `JN88TQ` returned `OM/TN-045` Bradlo at 4.2 km / 260° first after correcting pre-limit proximity ordering.
- Screenshots: `docs/portable/evidence/portable-all-on-air.png`, `docs/portable/evidence/portable-sota-places.png`, and `docs/portable/evidence/portable-wwff-map-detail.png`.

Phase 2B is integrated with the accepted status **PASS WITH EXTERNAL DEPENDENCY**. SOTA live remains unavailable pending written API approval; the disabled provider makes no live SOTA request. The existing offline summit catalogue is independent of that approval. WWFF full offline directory storage remains deferred pending permission. Phase 3A adds POTA Activate beside Chase without changing these provider boundaries; iPadOS parity, awards, panadapter overlays, and Nexus reuse remain outside Phase 2B.
