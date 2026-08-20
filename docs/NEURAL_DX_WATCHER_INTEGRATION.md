# Neural DX Watcher behavioural integration record

## Provenance and release status

- RigWeave repository: https://github.com/oliverbross/RigWeave
- Reviewed and implemented RigWeave base: `39a2926648bdd98ca3d8e1200eff4892dca5eee9`
- Behavioural reference: https://github.com/F1SMV/Neural-DX-Watcher
- Authorised task reference: `fe3cba8d613899fd605255e1ba8b748f42e2357e`
- Upstream revision actually present on `main` and in the inspected clone: `fe3cba8ed9c0502f5dabdb2f64ebd990de986559` (`version 12.1`). The authorised hash does not resolve in the upstream repository; owner confirmation is required before treating either hash as the approved pin.
- Upstream licence status: no `LICENSE`, `LICENCE`, `COPYING`, or `NOTICE` file, SPDX declaration, or licence grant was found in the inspected upstream tree or README. GitHub does not display a detected licence for the repository. Permission is therefore not established.
- Bundled upstream files or assets: none.
- This remediation copied no upstream source, comments, text, graphics, or assets. It changes RigWeave's existing native implementation and documentation only.
- The earlier RigWeave commit history identifies native Neural DX implementation commits, but source inspection alone cannot prove the provenance of every pre-existing line. Earlier provenance/permission requires owner confirmation.

> **Release blocker — unresolved upstream provenance and permission.** Do not describe the relationship as licensed parity or distribute material derived from Neural DX Watcher unless the owner confirms the correct immutable revision and the necessary permission/licence. RigWeave's internal worked-log correctness fix is valid independently of that unresolved release decision.

## Integration boundary

RigWeave uses Neural DX Watcher as a behavioural product reference. It does not embed the upstream Python/Flask application, HTML/CSS/JavaScript UI, SQLite files, predictor, resolver, deployment scripts, artwork, or runtime dependencies. RigWeave remains GPL-3.0-only.

| Reference concept | RigWeave implementation |
| --- | --- |
| Cluster spot ingestion and bounded live history | `FeatureController.kt`, `native_bridge.cpp`, `core/src/features.cpp`, `core/portable/src/dx_analysis.cpp` |
| Entity resolution | `CtyController.kt`, shared `CtyResolver` |
| Opportunity score/reason | shared `DxInsightEngine` in `dx_analysis.cpp` |
| Worked entity/call/band/mode intelligence | `QsoDatabase.kt`, shared `WorkedIndex` in `operator_intel.cpp` |
| Android seven-page workspace and local caches | `NeuralDxController.kt`, `NeuralDxScreen.kt`, `NeuralDxMap.kt`, separate `neural-dx.sqlite` |
| Apple DX surface | `FeatureModel.swift`, `QSOStore.swift`, `ContentView.swift` |
| Watchlist, solar and activity context | shared core plus the platform controllers above |

## Capability matrix

| Capability | Android | iOS | Desktop |
| --- | --- | --- | --- |
| Shared native live opportunities | Implemented | Implemented in the smaller DX view | Not implemented |
| Authoritative worked-log reload | Local log or selected cached Wavelog station | Current local `QSOStore` only | Not implemented |
| Worked-log loaded/complete health | Decoded and shown | Decoded and shown | Not implemented |
| Full seven-page Neural DX workspace | Implemented to differing provider depths | Not implemented | Not implemented |
| Separate `neural-dx.sqlite` cache | Preserved | Not added | Not implemented |

iOS cannot currently prove or select a Wavelog log authority in `QSOStore`; it loads only the local SQLite authority. No Wavelog station scope is fabricated.

## Corrected worked-log defect

Previously `rw_feature_add_worked_qso()` populated `WorkedIndex`, but `DxInsightEngine` ranked spots from a separate worked-country hash list. Android exposed no JNI operation to populate either native ranking authority. A spot already present in `QsoDatabase.spotStatuses()` could therefore receive the 22-point new-entity bonus and `NEW ENTITY IN LOGBOOK`.

The shared calculation now receives a per-spot classifier backed directly by `WorkedIndex`. It maps entity, call, band, mode, band+mode, and recent-dupe state into the opportunity before score and reason are calculated. `NEW ENTITY IN LOGBOOK` is possible only when the index is loaded, complete, and the resolved entity is non-empty and absent.

The reload contract is:

- `rw_feature_begin_worked_sync`: clears the prior index and marks it unloaded while rebuilding;
- `rw_feature_add_worked_qso`: normalises and records each local or Wavelog row, counting accepted and rejected input;
- `rw_feature_end_worked_sync`: atomically activates a legitimately empty or populated rebuild.

`workedLog.loaded` distinguishes never-loaded/loading from an empty log. `complete` is true only after activation with no rejected or truncated rows. An incomplete index may report positive matches it contains, but absence is never treated as proof of a new entity.

Android queries only callsign, stored country, frequency, mode, timestamp, band, and submode under the existing `stationScope()` rule. Current CTY resolution takes precedence over stored country; stored country is the fallback. A valid stored HF/6 m band takes precedence over frequency-derived band. A fingerprint of QSO change token, authority, selected Wavelog station, and CTY revision prevents unchanged periodic rebuilds. Native access is serialized while the complete rebuild is installed.

## Intentional differences from the behavioural reference

- RigWeave retains one active cluster connection with configured failover, not simultaneous multi-cluster aggregation.
- Shared native opportunity ingestion remains limited to HF and 6 m; VHF/UHF expansion is not part of this remediation.
- The full Neural DX workspace remains Android-only. iOS has the smaller native DX surface; desktop has none.
- RigWeave's opportunity score is a deterministic freshness/watchlist/entity/surge/solar heuristic. It is not the upstream `predictor.py` model, prediction database, verification history, or probability calibration.
- Android uses the selected RigWeave local or cached Wavelog authority. iOS currently uses only its provable local log authority.
- RigWeave preserves its existing native UI, local storage, controllers, and separate `neural-dx.sqlite`; it does not run Flask/nginx or an upstream local web API.

## Android external providers

Current source contains integrations for the configured DX cluster; NOAA SWPC solar products; country-files.com CTY data; Open-Meteo; wspr.live; PSK Reporter; DX-World; DXNews; NG3K ADXO; QO-100 DX Club; CelesTrak; AMSAT orbital elements; SatNOGS transmitters; the dl0tud beacon list; Blitzortung community MQTT; optional Perplexity; and an operator-configured HTTPS ntfy endpoint. Provider availability, terms, credentials, and live-service behaviour are separate from source/build validation.

## Future upstream review procedure

1. Obtain the owner's confirmed immutable upstream commit and explicit licence/permission status.
2. Fetch the upstream repository and record the exact commit, tree, licence files, notices, and release/version label.
3. Review behaviour and documentation as a reference; do not copy source or assets without a compatible licence and the repository's required provenance record.
4. Compare concepts against the mapping above and update the capability/deviation matrix truthfully.
5. Keep changes inside existing RigWeave authorities and storage unless a separate task authorises architecture work.
6. Run focused native, Android, and iOS validation and record source, automated, simulator/device, service, and RF evidence separately.

## Validation record for this remediation

- Shared core: configure/build passed; CTest `1/1` passed. Regression coverage includes unloaded, loaded-empty, matching-QSO, reset, score delta, and incomplete-index safety.
- Android JVM suite, debug APK, and instrumentation APK assembly passed.
- Android database instrumentation: the focused authority/station scope, entity/band fallback, and change-token test passed on the connected TB373FU. This is database evidence, not physical UI, RF, or service evidence.
- iOS generic Simulator build reached and compiled the changed shared core and FeatureModel sources, then failed in pre-existing unrelated `GroupsIoFeature.swift` explicit-`self` errors. The exact final result is recorded in the completion report.
- No live external provider, authenticated service, physical radio, transmission, or RF validation was required or performed.

Validation commands:

```sh
cmake -S core -B /tmp/rigweave-neural-dx-core
cmake --build /tmp/rigweave-neural-dx-core
ctest --test-dir /tmp/rigweave-neural-dx-core --output-on-failure

cd android
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug

xcodebuild -project ios/RigWeave.xcodeproj -scheme RigWeave \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
```
