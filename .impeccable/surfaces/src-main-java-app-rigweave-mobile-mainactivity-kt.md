---
version: 1
slug: "src-main-java-app-rigweave-mobile-mainactivity-kt"
primary_target: "android/app/src/main/java/app/rigweave/mobile/MainActivity.kt"
related_targets: []
---

Scope: Android Radio destination. Visitor mode: Operate.

Audience and job: A KX3/KX2 operator beside the physical radio must confirm live CAT state, read both VFOs, tune, adjust AF/RF/BW/power, invoke separate adjacent primary/secondary controls, and log a QSO without leaving the first landscape viewport.

Direction: A native Android interpretation of the Elecraft KX3 faceplate. One black machined chassis frames a full-width amber LCD between compact 4 × 3 key banks, with a continuous twelve-key receive strip below. Keys are raised rounded gray caps with a three-tone bevel, bright rim, off-white primary legend, and yellow secondary legend rather than flat square tiles. The LCD deliberately reproduces the physical display hierarchy rather than a dashboard: slim seven-segment VFO A at upper-right, subordinate VFO B beneath it, boxed A/B and mode/TX at the edge, separate S/CWT and SWR/RF instruments plus the filter trapezoid at left, and larger bold live annunciators between. The lower-right tuning deck mirrors the three physical groups AF/RF/MON, PBT I/WID plus PBT II/SHT/NORM, and KEYER/MIC/PWR around the VFO wheel directly above live DX. VFO A, mode, and BW legends are discoverable tap targets for band, mode, and mode-aware filter pickers. The wheel follows horizontal finger movement smoothly in both directions and sends CAT frequency updates continuously while it turns.

Constraints: Preserve observed radio truth, 48 dp touch targets, Android system insets, per-action confirmation for potentially transmitting controls, permanent Emergency RX, variable sub-kHz frequency precision, and no Panadapter, separate Spots, or Digital surface. Do not reintroduce a persistent `TX DISABLED` rail.

Validated: Bidirectional VFO tuning, matching wheel rotation, live CAT changes before release, and 100 Hz step behavior were exercised against the connected KX3 on the Lenovo tablet; operator preference can still tune sensitivity later.
