# Wavelog-native parity inventory

Status is relative to pinned Wavelog 3.1.0. `Complete` means a real RigWeave-native implementation exists; it does not mean authenticated or physical-device validation occurred.

| Surface | Baseline | This programme status | Placement or boundary |
|---|---|---|---|
| Local SQLite QSO journal and ADIF | Complete | Extended | Existing `QsoDatabase`; unknown ADIF fields now round-trip |
| Legacy Wavelog station/load/upload/pull | Complete | Preserved | Existing `WavelogController` |
| API v2 token/scopes/stations/QSO CRUD | Missing | Domain complete | `WavelogApiV2Client`; connection UX remains to be wired |
| Binding, remote links, outbox, checkpoints | Missing | Domain complete | Schema v8 and `WavelogSyncStore` |
| Three-way conflict detection | Missing | Domain complete | `WavelogSyncEngine`; conflict resolution UI remains |
| Initial, quick, full reconciliation | Legacy weaker | Domain complete | Resumable page checkpoints; remote deletion sweep remains limited by API surface |
| Date/station/call/mode/band/frequency filters | Complete | Preserved | Existing DB-backed `LogbookFilter` |
| QSL systems, DXCC/continent/zone/grid/state/county/IOTA/SOTA/POTA/WWFF/DOK/contest/text filters | Complete | Preserved | Existing DB-backed logbook filters |
| Invalid/deleted/duplicate-specific filters | Missing | Pending | Requires native query/UI extension |
| Bulk selected-QSO actions | Missing | Pending | No placeholder UI added |
| Simple Fast Log Entry parser | Missing | Pending | Native parser/editor required |
| Total/unique/year/month/day/mode/band/DXCC/grid/confirmation/distance | Present but split | Partly complete | Existing Progress covers most; operator and satellite/antenna summaries remain |
| DXCC/WAS/WAZ/QRP/POTA estimates | Complete as local estimates | Preserved | Existing Progress; never presented as official credit |
| Additional Wavelog award families | Missing | Pending | Rule-by-rule provenance and fixtures required |
| DX/contest calendars | Missing | Pending | Native provider policy required |
| POTA/SOTA/WWFF portable intelligence | Complete/partial by provider | Preserved | Existing Portable workflows; no fabricated SOTA live data |
| Satellite status/pass/flightpath | Missing | Pending | SGP4 dependency audit required; no automatic TX/Doppler action |
| Android native UI | Existing legacy/Sync Hub | Partial | Domain added; binding/conflict UI pending |
| iPhone/iPad local log/legacy sync/Keychain | Existing weaker | Preserved | Real API v2/reconciliation parity pending |
| Desktop | No real shell | Contract only | Do not claim a desktop UI |
| Upstream monitoring | Missing | Pending | Script and workflow required |

The implementation deliberately does not reproduce Wavelog HTML, Bootstrap layout, navigation, QSL-card image workflow, administration, server cron management, or multi-server writable synchronization.
