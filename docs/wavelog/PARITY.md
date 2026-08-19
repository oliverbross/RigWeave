# Wavelog-native parity inventory

Status is relative to pinned Wavelog 3.1.0. `Complete` means a real RigWeave-native implementation exists; it does not mean authenticated or physical-device validation occurred.

| Surface | Baseline | This programme status | Placement or boundary |
|---|---|---|---|
| Local SQLite QSO journal and ADIF | Complete | Extended | Existing `QsoDatabase`; unknown ADIF fields now round-trip |
| Legacy Wavelog station/load/upload/pull | Complete | Preserved | Existing `WavelogController` |
| API v2 token/scopes/stations/QSO CRUD | Missing | Android complete; Apple create/read complete | Native clients use Bearer tokens; Android also implements patch/delete domain operations |
| Binding, explicit local↔remote station mapping, remote links, outbox, checkpoints | Missing | Android complete | Schema v9, `WavelogSyncStore`, and `QsoMutationCoordinator` |
| Three-way conflict detection and resolution | Missing | Android complete | Persisted per-field values with Keep Local, Keep Remote, and per-field merged choices |
| Initial, quick, full reconciliation | Legacy weaker | Android complete | Resumable page checkpoints; only a completed Full scan may infer historic remote deletion |
| Date/station/call/mode/band/frequency filters | Complete | Preserved | Existing DB-backed `LogbookFilter` |
| QSL systems, DXCC/continent/zone/grid/state/county/IOTA/SOTA/POTA/WWFF/DOK/contest/text filters | Complete | Preserved | Existing DB-backed logbook filters |
| Invalid/deleted/duplicate-specific filters | Missing | Pending | Requires native query/UI extension |
| Bulk selected-QSO actions | Missing | Pending | No placeholder UI added |
| Simple Fast Log Entry parser | Missing | Android complete | Transactional preview/import with inherited context, shorthand, portable/contest fields, arbitrary ADIF, and explicit valid-only mode |
| Total/unique/year/month/day/mode/band/DXCC/grid/confirmation/distance | Present but split | Extended | Progress now adds operator, confirmation-source, satellite, and antenna summaries over the same filtered local snapshot |
| DXCC/WAS/WAZ/QRP/POTA estimates | Complete as local estimates | Preserved | Existing Progress; never presented as official credit |
| Additional Wavelog award families | Missing | Pending | Rule-by-rule provenance and fixtures required |
| DX/contest calendars | Missing | Pending | Native provider policy required |
| POTA/SOTA/WWFF portable intelligence | Complete/partial by provider | Preserved | Existing Portable workflows; no fabricated SOTA live data |
| Satellite status/pass/flightpath | Missing | Audited, not integrated | Pinned SGP4 candidate passed 168 tests; shared mobile packaging and native pass UI remain |
| Android native UI | Existing legacy/Sync Hub | Integrated | Explicit station mapping, bounded-vs-full truth, progress/resume/cancel, and field-level conflict resolution |
| iPhone/iPad local log/legacy sync/Keychain | Existing weaker | API v2 partial | Keychain `wl2_` tokens, capabilities, stations, and paginated reads exist; Wavelog 3.1.0 has no server idempotency, and Android Phase 1 reconciliation parity is not yet claimed |
| Desktop | No real shell | Contract only | Do not claim a desktop UI |
| Upstream monitoring | Missing | Complete | Read-only weekly/manual comparison fails for human review and verifies tracked pinned paths |

The implementation deliberately does not reproduce Wavelog HTML, Bootstrap layout, navigation, QSL-card image workflow, administration, server cron management, or multi-server writable synchronization.
