# RC1 Tablet Acceptance Sweep 1

## Scope

This acceptance sweep starts from immutable RC1 commit `8c085e979166d083283177d731a662a5424c7478` on `fix/rc1-tablet-acceptance-sweep-1`. It covers the protected Lenovo TB373FU installation, tablet UI, safe read-only services, deterministic debug surfaces, lifecycle behavior, packaging, and exact-SHA validation.

Raw screenshots, UI hierarchies, protected backups, logs, hashes, and service evidence remain outside Git under the timestamped local evidence root. No credential values or private Groups.io message bodies belong in committed documentation.

## Safety and preservation

- Preserve `app.rigweave.mobile` with `adb install -r`; never uninstall, clear data, or downgrade.
- Record UID, signer, first-install time, database schemas/counts, and preference-file hashes before and after install.
- Do not initiate CAT, PTT, TUNE, RF transmission, live TCI hardware connections, audio claims, or rotator movement.
- Debug Lab evidence is deterministic fixture evidence only and never proves physical radio, RF, audio, or rotator acceptance.
- Keep `main`, `v0.1.0-rc.1`, published release assets, and recovery refs unchanged.

## Issue matrix

| ID | Screen | Severity | Reproduction / evidence | Expected | Actual / root cause | Fix / test | Status |
|---|---|---:|---|---|---|---|---|
| RW-TAS1-001 | Launch / navigation | P1 | Force-stop after selecting Portable; baseline `001-home.png` and `001-home.xml` | Fresh activity opens Home in a disconnected, disarmed state | Last destination was persisted in `navigation` preferences | Make destination activity-local and initialize to Home; source contract test | FIXED |
| RW-TAS1-002 | Rotator | P1 | Open Rotator with no profile; `016-rotator.png` / `.xml` | Empty-state title and recovery guidance remain readable | Text inherited near-black colour on dark surface | Explicit high-contrast title/muted colours; source contract test | FIXED |
| RW-TAS1-003 | Radio / Band Maps | P2 | Inspect spot rows; `003-radio.png`, `007-band-maps.png` and hierarchies | Callsign status sits beside the call; each status uses its configured colour | Secondary line repeated `CS`/`DS` labels and used one cyan colour | Shared spot renderer places values after callsign and resolves CALL_STATUS/DXCC_STATUS colours independently; focused JVM test | FIXED |
| RW-TAS1-004 | Digi / DX Chaser | P2 | Inspect bottom path panel; `005-digi.png`, `036-dx-chaser.png` | Path visualization is readable and clearly identified | Fixed 112 dp strip and 10 sp combined label made the panel look like a tiny broken map | 176 dp preview, 13/12 sp hierarchy, and explicit non-map/non-RF-proof copy; source contract test | FIXED |
| RW-TAS1-005 | Intelligence / Spectrum, RF Map and RF Globe | P1 | Open all three Intelligence views; final screenshots and hierarchies | Each view has an understandable geographic reference, visible basemap, and honest evidence boundary | Spectrum was an unexplained black canvas, RF Map had no visible map, and the synthetic globe contained an oversized brown strip | Reuse the Home OpenFreeMap/MapLibre basemap, add labelled loading/error states, draw only bounded RF observation overlays, split dateline paths, and state that the global view is not a fabricated 3D globe; focused JVM tests | FIXED |
| RW-TAS1-006 | Settings / Radio profiles | P2 | Review available-radio selection | Users can browse a friendly manufacturer-grouped radio catalogue | Hamlib discovery/search is too implementation-led | Follow-up: grouped Elecraft, Kenwood, Yaesu, Icom, FlexRadio and other manufacturers with search retained | OUT_OF_SCOPE |
| RW-TAS1-007 | Feature and Settings pages | P2 | Contest and other feature pages link to additional settings surfaces | One coherent settings home per feature | Settings are fragmented and sometimes redirect to another settings surface | Follow-up: consolidate feature-owned settings and remove duplicate navigation | OUT_OF_SCOPE |
| RW-TAS1-008 | Physical radio/audio/RF/rotator | P3 | No hardware mutation authorized in this sweep | Claims require separately authorized capability/readback/physical evidence | Only disconnected/read-only and deterministic fixture evidence is in scope | Retain explicit pending labels and run the separate live-acceptance programme when authorized | LIVE_ACCEPTANCE_PENDING |
| RW-TAS1-009 | Settings / Screens | P2 | Open Settings, select Screens, hide an optional workspace, relaunch, then restore it | Users can tailor navigation without losing Home or Settings; choices persist and hidden screens are skipped safely | No screen-visibility controls existed | Add a persisted Screens tab with one switch per optional navigation workspace, default all visible, lock Home and Settings visible, skip hidden destinations in rail/bottom/hardware navigation, and return a newly hidden active screen to Home; focused JVM tests | FIXED |

No issue is silently omitted: the radio-catalogue and settings-consolidation requests are explicitly retained as post-RC1 follow-ups rather than being folded into the frozen acceptance candidate.

## Final gate

After the last source/documentation commit: run required Android validation, push the branch, record the exact SHA, build the arm64 APK embedding that SHA, verify pre-install invariants, install only with `adb install -r`, repeat the complete screenshot and interaction matrix, run the 45-minute unlocked soak, perform lifecycle/crash checks, audit packages, and complete exact-SHA hosted validation. Any post-install source correction invalidates the candidate and requires a new commit, rebuild, reinstall, device replay, and hosted replay.
