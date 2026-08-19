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
| Resumable initial/full scan and safe cancellation | Implemented | Durable checkpoint/seen set; restart fixture resumes page 2 | Database-backed test requires Android runner |
| Historic remote deletion only after completed Full scan | Implemented | Completed-scan seen-set sweep; interrupted scan fixture proves no inference | Database-backed test requires Android runner |
| Durable local tombstone and changed-remote delete conflict | Implemented | Schema v9 tombstone baseline; restore/keep-remote fixture | Database-backed test requires Android runner |
| Field-level conflict UI with Keep Local, Keep Remote, and Merged | Implemented | Native conflict card and engine resolution paths | Compose/device interaction not physically exercised |
| Automatic foreground and connectivity-return outbox drain | Implemented | Lifecycle and default-network callbacks start bounded quick sync with a 30-second guard | Device behaviour unverified |
| Honest Quick versus Full status, progress, cancellation, resume | Implemented | Native dialog/controller labels bounded overlap and full-history completion separately | Compose/device interaction unverified |
| Read-only divergence and station isolation | Implemented | Coordinator records blocked divergence and filters by mapped local station | Database-backed test requires Android runner |
| Migration and restart preservation | Implemented | v8→v9 migration fixture and reopened checkpoint fixture | Database-backed test requires Android runner |

## Phase 1 validation record

- Android JVM unit suite: PASS — 217 tests, 0 failures, 0 skipped.
- Android instrumentation source compilation: PASS.
- Android debug APK assembly: PASS with the stable Rust toolchain selected explicitly.
- Connected instrumentation: unavailable until an Android device or emulator is attached.
- Authenticated Wavelog 3.1.0 create/update/delete: not performed; no live token was supplied and no fake remote proof is claimed.
- Physical tablet acceptance: not performed in this phase.

## Later phases

Phases 2–5 remain governed by their prompt-pack gates and are not marked complete by Phase 1 work.
