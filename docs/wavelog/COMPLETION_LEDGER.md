# Wavelog completion ledger

Evidence is repository-local unless explicitly marked external. “Implemented” does not mean a live Wavelog account or physical tablet accepted the behaviour.

## Phase 1 — synchronization correction and Android completion

| Required outcome | Status | Repository evidence | Validation boundary |
|---|---|---|---|
| Canonical QSO is deterministic and lossless for Unicode, multiline text, colons, empty values, and unknown ADIF | Implemented | `WavelogNativeModels.kt`; JVM round-trip and legacy-decoder fixtures | Local contract only |
| Exact Wavelog 3.1.0 JSON create and remote ID capture | Implemented | `WavelogApiV2.kt`; create contract fixture | No authenticated write |
| No invented server idempotency | Implemented | Transport has no idempotency header; ambiguous creates block and reconcile by natural key | Pinned 3.1.0 source reviewed |
| Explicit PATCH whitelist and Hz conversion | Implemented | `wavelogJsonFields`, `jsonPatchBody`; contract fixture | Local contract only |
| One coordinator for normal logger, POTA, Fast Entry, ADIF import, correction, and delete | Implemented | `QsoMutationCoordinator.kt`; production call-site scan and database-backed fixtures | UI/device interaction not physically exercised |
| Explicit local station ↔ remote Wavelog station mapping | Implemented | Native dialog mapping controls and binding persistence | Requires operator selection |
| Atomic create link/baseline/acceptance | Implemented | `acceptWithLink`; database-backed create/update/delete fixture | No authenticated write |
| Ambiguous create recovery without blind retry | Implemented | Blocked create plus unique natural-key page reconciliation fixture | Network failure simulated |
| Conservative Full scan and safe cancellation | Implemented | Two complete page-1 passes must produce identical remote-ID inventories; cancellation and concurrent-insert fixtures refuse deletion inference | Database-backed test requires Android runner |
| Historic remote deletion only after stable Full inventory | Implemented | Two-pass seen-set comparison plus stable deletion sweep; shifting newest-first inventory fixture preserves local data | Database-backed test requires Android runner |
| Explicit local-only or guarded remote deletion | Implemented | Schema v10 intent tombstone; Logbook choice; missing-scope, changed-remote, and ambiguous-delete fixtures | Database-backed test requires Android runner |
| Convergent field-level conflict UI with Keep Local, Keep Remote, and Merged | Implemented | Baseline/local/remote conflict card; durable pending intent; acceptance and restart fixtures | Compose/device interaction not physically exercised |
| Automatic foreground and connectivity-return outbox drain | Implemented | Lifecycle and default-network callbacks start bounded quick sync with a 30-second guard | Device behaviour unverified |
| Honest Quick versus Full status, progress, cancellation, resume | Implemented | Native dialog/controller labels bounded overlap and full-history completion separately | Compose/device interaction unverified |
| Read-only divergence and station isolation | Implemented | Coordinator records blocked divergence and filters by mapped local station | Database-backed test requires Android runner |
| Migration and restart preservation | Implemented | v8→v9 migration fixture and reopened checkpoint fixture | Database-backed test requires Android runner |
| Binding UPSERT and complete lifecycle | Implemented | In-place update preserves all six child table types and nullable timestamps; paused/configured queries; remap, pause/resume, reset, and remove controls | Compose/device interaction not physically exercised |
| Native outbox and operational errors | Implemented | CREATE/UPDATE/DELETE identity, state, attempts, retry time, safe error class, conflict/tombstone relation, invariant warning, and ambiguity-safe actions | Compose/device interaction not physically exercised |
| Schema v9→v10 migration | Implemented | Migration fixture covers outbox error class, conflict intent fields, and deletion intent | Database-backed test requires Android runner |

## Phase 1 validation record

- Android JVM unit suite: PASS — 217 tests, 0 failures, 0 skipped.
- Android instrumentation source compilation: PASS.
- Android debug APK assembly: PASS with the stable Rust toolchain selected explicitly.
- Connected instrumentation: unavailable until an Android device or emulator is attached.
- Authenticated Wavelog 3.1.0 create/update/delete: not performed; no live token was supplied and no fake remote proof is claimed.
- Physical tablet acceptance: not performed in this phase.

## Later phases

Phases 2–5 remain governed by their prompt-pack gates and are not marked complete by Phase 1 work.
