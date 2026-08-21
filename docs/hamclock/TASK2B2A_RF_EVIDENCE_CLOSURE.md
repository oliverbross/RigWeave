# Task 2B2A — RF evidence correctness closure

Date: 2026-08-20

## Repository and watcher

- Requested baseline: `63782075ce5a154404d5ff2a5956e9ba1a687732`.
- Safe start history retained all five commits through `c524aab2260485cc3196e875208fad4282eab018`: `19f19b3` database startup, `a59d75d` RF truth, `c2a8c9e` Home integration, `8b026fb` closure records, and `c524aab` contributing DX News sources.
- Final pushed SHA is recorded in the delivery report because a commit cannot contain its own hash.
- Pre-edit upstream watcher: exit 0; reviewed pin unchanged.

## Closure

- Coalescing completes before exact removal.
- RBN has typed views, line/receipt time, short-bucket dedup, DX/skimmer endpoint truth, cadence publication, watchlist republish and no cluster double count.
- PSK/personal WSPR preserve direction states; mixed availability is `DEGRADED`; local filters and station geometry reproject without requests.
- Home DX News consumes the merged snapshot before source filtering.
- IBP uses the literal 18-row manifest, KH6RS `BL10TS`, fixed hash `c5a6333fca305bf35c4e9ded6a3c0885b0b217a6513b263f78923a34931fdc41` and distinct schedule/observed evidence.
- Band Health excludes QSO history from live sources, deduplicates cross-source events and separates source count/call/receiver diversity. Saved settings drive the DX workspace.
- Exact RBN, WSPR and observed-IBP dialogs provide Logbook history; all frequency actions enter receive review. Expired exact requests are consumed.
- Foreground loss stops Neural DX, lightning, satellite, PSK and WSPR jobs; foreground restoration restarts bounded authorities.
- Home uses real OpenFreeMap dark geography, a center-filling map, wider rails, approximately 50% larger box typography, taller clipped panels and a shorter DX-target module.

## Evidence boundary

No APK was installed and no app data was cleared. Tablet layout, live providers, authenticated Wavelog/QRZ and RF reception remain external evidence. Development installs must remain in-place replacements; credentials must never be removed, reset, logged or copied into evidence.

## Final validation

- Final upstream watcher: exit 0; no pin movement.
- `testDebugUnitTest`: 291 tests, zero failures/errors/skips.
- `assembleDebug`: passed after placing the rustup toolchain on `PATH`.
- APK: `android/app/build/outputs/apk/debug/app-debug.apk`, 113,607,354 bytes, SHA-256 `43499a2a1364c38cf983cafc30244db78682aa65652c5ee230ab1f6843dee523`.
- Impeccable mechanical detector: no findings.
- `git diff --check`, HamClock `upstream.json` parse and added-literal credential scan: passed.
- Clean worktree and local/remote equality are recorded after commit and push in the delivery report.
