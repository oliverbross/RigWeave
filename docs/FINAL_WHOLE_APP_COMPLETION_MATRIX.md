# Final whole-application completion matrix

## Multiplatform candidate delta

Hardened Android and Windows Desktop Alpha are integrated without changing either source branch or `main`. Windows has a bounded local service graph, schema-16 semantic contract, fake-service Wavelog coverage and hosted packaging path; full desktop parity and physical/live acceptance remain incomplete. Native SwiftUI iOS and macOS Qt proof are separate build layers.

## Tablet Acceptance Sweep 2 delta

Sweep 2 adds Contest schema-2 staging/merge, validated private SCP, Band Maps v2 and shared Radio ladder, Intelligence presentation/map corrections, typed portable catalogue truth and Groups.io foreground sync settings. Final hosted/device evidence remains governed by `TABLET_ACCEPTANCE_SWEEP_2_LIVE_CHECKLIST.md` and cannot be inferred from source completion.

## Tablet Acceptance Sweep 1 delta

The fix/tablet-acceptance-sweep-1 source closes the 41 owner-observed acceptance items recorded in TABLET_ACCEPTANCE_SWEEP_1.md: Home/Settings convergence, complete Contest workspaces, interactive Band Maps, authority-scoped Log Intelligence, tablet layouts, Portable map/browser corrections, catalog-truth Operations, Groups.io server timestamps, and shared CS/DS presentation. This row is source-complete only; exact-SHA hosted, protected-tablet presentation, authenticated-service, audio and RF evidence remain separate gates.

| Area | Classification | Evidence boundary |
|---|---|---|
| Android production graph and navigation | SOURCE_COMPLETE | One construction graph; each required top-level destination appears once; DX Chaser remains inside Digi |
| Typed operating context and handoffs | SOURCE_COMPLETE | Generation-safe router; Band Maps supports RX review, DX, history, Callbook, Contest, Digi, Chaser and portable handoffs without action authority |
| Band Maps | SOURCE_COMPLETE | Snapshot consumers, 20,000-input cap, bounded projections, preferences-only persistence, no network/spot DB/TX owner |
| Keyer / Contest / N1MM / DX Chaser / Digi | SOURCE_COMPLETE | Canonical mutation path, explicit arms, local-decode eligibility and idempotent global Stop |
| Wavelog / Neural / HamClock / Groups.io / Operations / Satellites | SOURCE_COMPLETE | Existing owners retained; regression gates remain mandatory |
| Configuration / Health / privacy | SOURCE_COMPLETE | Safe-section fixture, previewed transactional restore, metadata-only diagnostics/support bundle |
| Physical tablet, radio, audio, live services and RF | LIVE_ACCEPTANCE_PENDING | Never inferred from source or builds; use `FINAL_INTEGRATED_LIVE_ACCEPTANCE.md` |
| Full Apple parity for Contest/Band Maps/Chaser | PLATFORM_FUTURE | Unified Apple build is still mandatory |
| Local P.533 | LICENCE_BLOCKED | No ITU payload; existing `LICENSE_BLOCKED` truth retained |
| APRS, Winlink, WWBOTA, hazards, Meshtastic/MeshCom, extra Digi modes | OPTIONAL | Explicitly outside this release programme |
| Desktop shells | EXCLUDED | Windows/macOS/Linux shells are not part of this convergence |

Mandatory build, watcher, package, scale, hosted exact-SHA, push and device results are recorded in `RIGWEAVE_FINAL_WHOLE_APP_CONVERGENCE.md`; a source-complete row alone is not release evidence.

## Sweep 2 radio and rotator integration

| Area | Verdict | Boundary |
|---|---|---|
| Stable radio profiles and one owner | SOURCE_COMPLETE | Native KX/Flex/QMX/RGO plus dynamic Hamlib profiles; restore is disconnected. |
| QMX/QMX+ | SOURCE_COMPLETE; LIVE_ACCEPTANCE_PENDING | CAT/controller wired; exact UAC/IQ/audio and hardware evidence pending. |
| RGO ONE | SOURCE_COMPLETE; LIVE_ACCEPTANCE_PENDING | V6 requires explicit profile plus ID 006; legacy/unknown is read-only. |
| Hamlib radio and rotator | SOURCE_COMPLETE | One vendored 4.7.2 archive/JNI bridge; physical models pending. |
| Rotator workspace and safety | SOURCE_COMPLETE; MOTION_PENDING | One owner, explicit motion review, session-only automation, immediate STOP. |
| Protected tablet | DEVICE_ACCEPTANCE_PENDING | Backup, `adb install -r`, launch and persistence evidence required. |

Sweep 3 makes Radio, Hamlib, QMX/QMX+, RGO ONE, Rotator and WWFF operator-reachable through Settings and workspace routes. CQ/ITU/state geometry remains `PROVIDER_BLOCKED / UNAVAILABLE_DATA`; physical radio, RF and rotator-motion acceptance remains pending and is never inferred.

## Android lifecycle hardening delta

| Area | Verdict | Boundary |
|---|---|---|
| JNI handle owners | SOURCE_COMPLETE; SANITIZER_PASS | Feature, base CAT, Digi, Flex, Panadapter and Hamlib radio/rotator use checked retirement; stateless satellite/propagation calls remain stateless. |
| Audio, map and browser lifecycle | SOURCE_COMPLETE | Audio callbacks retire before release; seven maps and two WebViews reject late callbacks and dispose deterministically. |
| QSO schema 16 | SOURCE_COMPLETE; REOPEN_FIXTURE_COMPILED | Canonical row, projection relationship and settings metadata survive close/reopen; no downgrade/recreate. |
| Hosted exact SHA | PASS | Release-candidate run `32784249372` passed all seven jobs at `826ba3031d869f12e0c9d37649257f9b2fac1ecf`; the final documentation-only tip is rerun externally. |
| Protected tablet | PROCESS_ACCEPTANCE_PASS; VISUAL_NAVIGATION_BLOCKED | Backup/hash, compatible in-place install, UID/data/schema/QSO preservation, relaunch cycles and locked-state process soak pass. Secure keyguard prevented visible workspace navigation and true unlocked foreground-provider soak. |
| Authenticated services, physical audio/radio/RF/rotator | LIVE_ACCEPTANCE_PENDING | Never inferred from source, package, process or navigation evidence. |

## Windows desktop full-parity v1 addendum

| Layer | Status | Evidence |
|---|---|---|
| Windows navigation/provider/data foundation | SOURCE_COMPLETE | 19 destinations, one owner graph, five versioned domain stores, 17 bounded providers, global Stop. |
| Android feature parity | PARTIAL | 14/31 audited rows are source-complete; 17 remain wired foundations. |
| Local desktop/gallery/scale | PASS | 6/6 tests; 75 distinct frames; 100k QSO and feature scale fixtures. |
| Windows package | HOSTED_PENDING | Exact-SHA Windows ZIP/NSIS workflow added; local macOS host cannot produce acceptance-grade Windows artifacts. |
| Live hardware/services | PENDING | No authenticated service, audio, CAT/PTT/TUNE, RF or movement acceptance performed. |
