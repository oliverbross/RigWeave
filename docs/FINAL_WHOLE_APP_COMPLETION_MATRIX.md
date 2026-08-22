# Final whole-application completion matrix

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
