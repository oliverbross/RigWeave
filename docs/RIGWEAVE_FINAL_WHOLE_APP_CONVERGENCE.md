# RigWeave final whole-application convergence

## Frozen source and branch

- Original main: `b4f12e17fa87df16d2094b518ae187553e370be5`
- Semantic integration: `98490b6d5234c3f12cc5d00bbea3163c8273c3dc`
- Intelligent Band Maps: `38871cec8a08d3ef2711c8db9c3eda4d45996afa`
- Final branch: `integration/rigweave-final-whole-app-v1`, created directly from the Band Maps tip
- Verified ancestry: main → semantic integration → Band Maps; Keyer, Contest/N1MM and DX Chaser tips are ancestors; semantic…Band Maps count `0 4`

No contained feature branch was merged again, and no rebase, squash, force push, store release, credential access or data clearing is permitted.

## Source convergence

Production retains one construction/lifecycle authority per concern. The typed workspace router now represents every top-level workspace plus contextual Callbook, Chaser and Satellite targets, rejects a stale expected operating-context generation, and exposes no PTT/TUNE/TX/log/post capability. Band Maps can prepare an exact band/callsign/preset and exposes receive review, DX details, history, Callbook, Contest, Digi, Chaser and portable actions while remaining a snapshot-only consumer.

Navigation is Home, Radio, Digi, Panadapter, EQ, Logbook, Log Intelligence, Sync, Contest, Band Maps, Presets, DX, Portable, Operations, Groups.io and Settings. Contest and Band Maps default visible; DX Chaser remains inside Digi; N1MM remains inside Contest/Health; QO-100 remains in Operations/Satellites.

Contest local mutation remains `QsoMutationCoordinator` → canonical local success → Contest link/revision → serial commit → derived state/N1MM receipt. Wavelog delivery remains the single canonical outbox. DX Chaser cannot enable Digi TX and completes only from canonical QSO success. Global Stop remains the sole idempotent stop/RX-request path.

Configuration includes Keyer, Contest/N1MM safe preferences, DX Chaser, Band Maps and destination visibility; unsafe arms, queues, sessions, peers, claims, live observations and radio commands are excluded/reset. Health/support output is bounded metadata only. Database schemas remain QSO 13, projection 2, Neural 5, Digi 2, Groups.io 2, Contest 1 and DX Chaser 1; Band Maps adds no database.

## Evidence ledger

This ledger must be updated from actual command results before main is eligible. Source review is not build, device, service, audio or RF evidence.

- Release contract and repository scans: PASS after convergence edits; `git diff --check` clean and prohibited full-log/WSPR.live/P.533/conflict-marker scans empty.
- Android unit tests: PASS after convergence edits (`BUILD SUCCESSFUL`, 45s incremental).
- Android assemble, bundle, Android-test compile/assemble and lint: PASS; lint recorded 0 errors and 171 warnings.
- APK: 111,832,640 bytes; SHA-256 `f84fd0c7706012fb8c90d9ff1036d91aad9603691dd5cc07625d4b3425f6b41e`.
- AAB: 53,102,106 bytes; SHA-256 `896058fa90d5b9ad4d0a1d99ed6ca79a4f9ef57c9aef68e2c3c20e2b8c7ddb23`.
- Rust: PASS, 97 passed / 0 failed / 1 ignored. Shared core: BLOCKED locally because `cmake` is unavailable; no result is inferred.
- Apple unsigned generic iOS Simulator build: `BUILD SUCCEEDED`.
- Wavelog: NO CHANGE. MSHV Auto DX Chaser: NO_REVIEW. OpenHamClock stable/release/licence unchanged; preview-only satellite-layer/test movement is documented and not absorbed. Nexus moved from reviewed `57d11fd`/1.7.5 to `f0869a1`/1.7.6 in documentation, Digi workflow, waterfall/audio and UI paths; no source was absorbed and review remains pending.
- Deterministic release soak: PASS for 100k logbook, 180-day Neural, 20k Digi, 30k Groups.io and provider lifecycle profiles; generated data was disposable.
- Hosted exact-SHA workflow: pending until the branch is pushed.
- Protected tablet in-place deployment: explicitly authorised, but ADB reported no attached device. The required `pm path`/backup gate could not run, so no install was attempted.
- Physical radio/audio, authenticated Wavelog/Groups.io, live N1MM peer and RF: pending; not claimed by this programme.
