# Desktop Visual Acceptance Report

## Evidence layers

| Layer | Evidence | Current result |
|---|---|---|
| Tablet source | 41 new 2944×1840 unlocked captures, resolution/non-blank check | PASS as visual reference |
| Exact-base desktop | macOS and Windows base artifacts at `ce1b99f`; 114 frames/platform | PASS as before evidence |
| Source/UI contract | command registry, icon resources, responsive shell, accessibility names, gallery completeness | Pending final exact SHA |
| Hosted Windows | Build, CTest/QML, smoke, four 39-frame profiles, portable ZIP and NSIS | Pending final exact SHA |
| Hosted macOS | Build, CTest/QML, smoke through test stage, five 39-frame profiles, unsigned app ZIP | Pending final exact SHA |
| Local macOS after artifact | Launch, native app menu, shell/workspace/compact mode screenshots | Pending final artifact |

## Acceptance criteria

- Every workspace is reachable through the native Navigate menu, shortcuts, and the command palette without a persistent global side rail.
- macOS has no in-window Windows menu and the app/window identity is “RigWeave”.
- Windows exposes a native File/Edit/View/Radio/Navigate/Tools/Window/Help menu in window chrome with Alt access.
- Every command icon resolves from the packaged original SVG family.
- Written status accompanies colour; focus borders and accessible names remain present.
- 1366×768 and an effective 1280×720 viewport at 150% retain every destination and Global Stop.
- Deterministic galleries remain isolated and do not read production credentials or operate hardware.

## Explicit non-claims

Visual acceptance does not prove authenticated Wavelog/Groups/providers, live audio, CAT/PTT/TUNE, Digi transmit, RF output, physical Windows rendering, multi-monitor use, screen-reader behavior on physical machines, or rotator movement. Those layers retain their existing pending/blocked status.
