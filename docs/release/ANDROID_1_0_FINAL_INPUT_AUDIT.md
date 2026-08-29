# Android 1.0 final input audit

## Frozen identities

| Input | Required SHA | Verified |
|---|---|---|
| RC1 tablet acceptance | `b1c01df63e877b901b2e534c8ba7647f77613402` | local and `origin/fix/rc1-tablet-acceptance-sweep-1` |
| Android 1.0 hardening | `cf9e78d010ee673baf4f5aae44a422bba040c6ca` | local and `origin/release/android-v1-hardening` |
| Canonical `main` at start | `8c085e979166d083283177d731a662a5424c7478` | local and `origin/main` |
| `v0.1.0-rc.1` | `8c085e979166d083283177d731a662a5424c7478` | peeled tag unchanged |

The canonical main, tablet-acceptance, and hardening worktrees were clean at preflight. The integration branch was created directly from the hardening SHA in the required isolated worktree.

## Ancestry result

`git merge-base --is-ancestor b1c01df… cf9e78d…` returned exit 0. The merge base is the tablet-acceptance SHA itself, so this is programme Case A: the sweep is already contained and must not be merged again.

The hardening-only range contains three commits:

1. `aafc48e` — harden Android 1.0 release candidate.
2. `3dad909` — align Android 1.0 data contract gates.
3. `cf9e78d` — preserve QSO display semantics in projection.

## Accepted tablet behavior equivalence

| Required behavior | Classification | Production evidence | Focused regression evidence |
|---|---|---|---|
| Spectrum reuses Home MapLibre/OpenFreeMap geography | `ALREADY_PRESENT_EQUIVALENT` | `AndroidSdrWorkbenchScreensV4.kt`, `RfEvidenceBasemap.kt` | `RfEvidenceBasemapTest` |
| RF Map reuses Home basemap | `ALREADY_PRESENT_EQUIVALENT` | `AndroidSdrScreens.kt`, `RfEvidenceBasemap.kt` | `RfEvidenceBasemapTest` |
| RF Globe flat/world surface uses MapLibre context honestly | `ALREADY_PRESENT_EQUIVALENT` | `AndroidSdrScreens.kt`, `RfEvidenceBasemap.kt` | `RfEvidenceBasemapTest`, `TabletAcceptanceSweep1CoreTest` |
| Radio/Band Map status labels remain compact and adjacent | `ALREADY_PRESENT_EQUIVALENT` | `MainActivity.kt`, `bandmap/BandMapScreen.kt` | `TabletAcceptanceSweep1BandMapTest` |
| configured CS/DS colors remain independent | `ALREADY_PRESENT_EQUIVALENT` | `SpotWorkedStatus.kt`, `MainActivity.kt`, `BandMapScreen.kt` | `TabletAcceptanceSweep1CoreTest`, `TabletAcceptanceSweep1BandMapTest` |
| Settings → Screens exists | `ALREADY_PRESENT_EQUIVALENT` | `MainActivity.kt` `SettingsSection.SCREENS` | `WorkspaceScreenVisibilityTest` |
| hide/show persists across relaunch | `ALREADY_PRESENT_EQUIVALENT` | versioned private preference in `AppController.kt` | `WorkspaceScreenVisibilityTest`; prior exact-SHA physical sweep |
| Home/Settings stay visible and hidden active screen safely returns Home | `ALREADY_PRESENT_EQUIVALENT` | `normalizeHiddenWorkspaceScreens` and destination effect | `WorkspaceScreenVisibilityTest` |
| scrollable route reachability | `ALREADY_PRESENT_EQUIVALENT` | current navigation owner in `MainActivity.kt` | `NavigationRailInstrumentedTest` |
| safe Home launch and device-only lifecycle fixes | `ALREADY_PRESENT_EQUIVALENT` | activity-local Home destination and retained map owners | `TabletAcceptanceSweep1CoreTest` and hardening lifecycle suites |

No accepted sweep behavior required semantic reapplication, and no legacy file was merged over the hardening architecture.

## Hardening contract retained

- Android remains `app.rigweave.mobile`, version `1.0.0`, version code `40`.
- R8/resource shrinking, schema 17/projection contract 6, grouped Hamlib catalog, real serial/TCP/rotctld/Hamlib rotator profiles, consolidated Contest/Digi/DX Chaser settings, adaptive Settings navigation, lifecycle/concurrency fixes, and accessibility repairs remain present.
- The hardening evidence baseline is 35,461,691-byte arm64 APK, 55,125,572-byte four-ABI AAB, 8,095,840 uncompressed DEX bytes, 1.25 ms callsign median, and 62.98 ms worked-log median.
- Signing compatibility is not changed by source. Final candidate signer/installed signer equality remains a protected-device gate.

## Decision

No merge commit, cherry-pick, blanket conflict resolution, or duplicate tablet code is permitted or required. The final branch changes are limited to CI closure, reviewed watcher provenance, truthful product/release documentation, tests, and any defect proven by final validation.
