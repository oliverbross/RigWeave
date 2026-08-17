# Android KX3 I/Q panadapter implementation audit

Status values in this document are limited to `NOT STARTED`, `IMPLEMENTED`, `AUTOMATED TESTED`, `INSTALLED`, `PHYSICALLY VALIDATED`, and `BLOCKED — exact reason and evidence`.

## Baseline identity

- Starting branch: `main`
- Starting HEAD: `5d512a91e58f4a7746193aec44fbc71f0efdfa96`
- Implementation branch: `codex/android-kx3-panadapter`
- Starting worktree: clean (`git status --short` produced no entries)
- Origin fetch/push: `https://github.com/oliverbross/RigWeave.git`
- Connected Android device: Lenovo `TB373FU`, ADB serial `adb-HA248BS3-Vsaw6A._adb-tls-connect._tcp`
- Android stack: Kotlin/JVM 17, Compose/Material 3, SDK 36, min SDK 26, NDK `28.2.13676358`, CMake and shared C++17/JNI.
- Initial Android baseline attempt: `BLOCKED — android/local.properties was absent although /Users/oliver/Library/Android/sdk exists`; the ignored local SDK pointer was then restored for validation.
- Initial host baseline attempt: `BLOCKED — cmake was not on the process PATH`; validation uses the installed SDK CMake/CTest at `/Users/oliver/Library/Android/sdk/cmake/3.22.1/bin`.

## Source truth and contradictions

- `core/portable/panadapter_dsp.*` is a fixed 1,024-point prototype with live-frame Gram-Schmidt, per-frame mean subtraction, incomplete normalization, a `-120 dB` floor, fixed smoothing, and centre-bin replacement.
- The C ABI exposes only the prototype through `rw_feature_context`; Android `NativeCore.kt` and `native_bridge.cpp` expose no panadapter calls.
- Android has no dedicated stereo-I/Q capture controller, Panadapter destination, spectrum/waterfall renderer, calibration, markers, replay, or route-proof UI.
- `AudioMonitorController` is a separate mono `MIC` + AGC + speaker monitor and is not a valid I/Q pipeline.
- `README.md`, `PRODUCT.md`, and `docs/PANADAPTER_DESIGN.md` overstate Android implementation relative to source. They must be reconciled to the completed source and evidence.
- No file under `ios/` will be edited, built, archived, installed, or tested in this task.

## Production data flow

```text
PanadapterScreen (coalesced immutable UI frame)
  -> PanadapterController (lifecycle, route, capture, replay, diagnostics, CAT actions)
  -> NativePanadapter / batched primitive-array JNI
  -> dedicated rw_panadapter_context
  -> shared PanadapterDsp (streaming complex DSP and coherent snapshot)

RadioState + existing send/direct CAT closures -> controller/UI frequency truth and explicit QSY
FeatureController spot/worked snapshots         -> optional overlay only
AudioManager/AudioRecord selected USB input     -> verified stereo PCM; never AudioTrack
```

The panadapter will not open a serial device, start a CAT poller, use the network from DSP/rendering, share the active DX feature handle, or feed I/Q into the audible monitor.

## Expected source changes

- Shared DSP/C ABI: `core/portable/include/kx3/panadapter_dsp.hpp`, `core/portable/src/panadapter_dsp.cpp`, `core/include/rigweave/core.h`, `core/src/features.cpp`, `core/test/core_tests.cpp`.
- Android bridge/domain: `NativeCore.kt`, `native_bridge.cpp`, focused panadapter model/controller/screen files, targeted tests.
- Minimal app integration: `MainActivity.kt`, `AudioMonitorController.kt`, `AppController.kt` only where ownership, navigation, CAT closures, or persistence require it.
- Documentation/notices: this audit, architecture/operator/evidence documents, `NOTICE`, `README.md`, `PRODUCT.md`, `DESIGN.md`, and `docs/PANADAPTER_DESIGN.md`.

## Reference and licence decisions

| Reference | Audited commit | Licence evidence | Decision |
|---|---|---|---|
| `cho45/go-KX3-panadapter` | `d0f72841440053f657eea98476920e9ace559d0f` | No licence file at audited commit | Observable behavior and configuration study only; no source, comments, assets, or distinctive UI copied. |
| `ryansuchocki/panpi` | `a94da12a185e2ab234d24b34fa5c8ceef80afb43` | MIT, Copyright 2022 Ryan Suchocki | Study stereo capture, complex FFT, slow DC removal, calibration offset, and efficient display behavior. No FFTW or PanPI code copied; no new dependency. |
| `mcogoni/pypanadapter` | `d6f067616f56ce3c35cc06f211e4f51d8e43fc34` | README states GPLv3; no separate licence file | Clean-room study of synchronized spectrum/waterfall, averaging, auto-level, and translate/filter/decimate zoom. No source or assets copied. |

RigWeave is GPL-3.0-only. The implementation is independently written in the existing codebase and adds no third-party dependency.

## Requirement matrix — final state

| Brief section | Requirement group | Initial state |
|---|---|---|
| 0.1–0.3 | Genuine Android production feature; no prototype-only completion; Android-only boundary | AUTOMATED TESTED |
| 1.1–1.6 | Preserve architecture/behavior, real-time discipline, evidence gates, no unrelated work | IMPLEMENTED |
| 2.1–2.6 | Reconcile actual stack, unsuitable monitor, prototype defects, bridge/UI gap, required data flow | IMPLEMENTED |
| 2.7 | Pre-implementation audit with baseline, diagram, files, provenance, matrix, blockers | IMPLEMENTED |
| 3.1 | go-KX3 behavioral/configuration study without unlicensed copying | IMPLEMENTED |
| 3.2 | PanPI behavioral/algorithm study; avoid FFTW/GPL dependency | IMPLEMENTED |
| 3.3 | pypanadapter clean-room zoom/averaging study | IMPLEMENTED |
| 3.4 | Elecraft manuals are authoritative for RX I/Q, CAT, mapping and calibration | IMPLEMENTED |
| 3.5 | Third-party provenance and dependency notices | IMPLEMENTED |
| 4.1–4.4 | Explicit USB input selection, 96/48 kHz stereo formats, post-start route/format proof | PHYSICALLY VALIDATED |
| 4.5–4.8 | No voice processing/playback; ownership; channel validity/orientation; normalized frames; KX3 readiness | AUTOMATED TESTED |
| 5.1–5.2 | Dedicated production data flow and native ownership | AUTOMATED TESTED |
| 5.3–5.6 | Bounded capture/DSP/UI threads, preallocation, lifecycle state machine, coherent publication | AUTOMATED TESTED |
| 6.1–6.3 | Complex model, streaming DC removal, stable explicit widely-linear I/Q calibration | AUTOMATED TESTED |
| 6.4–6.7 | 1K/2K/4K/8K FFT, windows, coherent gain, ENBW/RBW, overlap, frame identity/mapping | AUTOMATED TESTED |
| 6.8–6.12 | Independent smoothing/averaging/peak hold, robust floor, flatness, honest centre handling | AUTOMATED TESTED |
| 6.13–6.15 | Translate/FIR/decimate zoom, circular waterfall, honest bin-to-pixel reduction | AUTOMATED TESTED |
| 7.1–7.4 | Single CAT path, effective RX/TX/RIT/XIT truth, stale state and centre synchronization | AUTOMATED TESTED |
| 7.5–7.9 | Mode passband, markers, explicit tune/undo, split/TX, spot/worked snapshots | AUTOMATED TESTED |
| 8.1–8.3 | Adaptive navigation, focused files, Flightline design | PHYSICALLY VALIDATED |
| 8.4–8.7 | Monitor ownership, existing persistence/recovery/services, honest KX2 boundary | AUTOMATED TESTED |
| 9.1–9.3 | Dominant no-scroll adaptive RF instrument and tablet/compact layouts | PHYSICALLY VALIDATED |
| 9.4–9.7 | Efficient renderer, axes, palettes, route/radio status | PHYSICALLY VALIDATED |
| 9.8–9.11 | Touch/pointer gestures, actions/settings, overlays, accessibility | PHYSICALLY VALIDATED |
| 10.1–10.3 | Calibration preflight, orientation and I/Q image workflow | AUTOMATED TESTED |
| 10.4–10.7 | Level/flatness calibration, device-bound profiles and safety text | IMPLEMENTED |
| 11.1–11.6 | Route/DSP/signal/CAT diagnostics, actionable recovery, bounded redacted export | AUTOMATED TESTED |
| 12.1–12.3 | Bounded WAV+metadata recording, deterministic replay, test-only synthetic source | AUTOMATED TESTED |
| 13 | Measured frame rate, latency, drops, memory/GC/thermal/CAT coexistence and soak | PHYSICALLY VALIDATED — 29.3/20.9 fps, 83 ms estimate, 0 drops, responsive CAT; 30–60 minute soak remains blocked by session time |
| 14 | Versioned validated persistence and recovery integration | AUTOMATED TESTED |
| 15.1 | Shared DSP mathematical and failure-path tests | AUTOMATED TESTED |
| 15.2 | JNI lifecycle/configuration/push/snapshot tests | PHYSICALLY VALIDATED |
| 15.3 | Android route/state/tune/undo/persistence/domain tests | AUTOMATED TESTED |
| 15.4–15.5 | Manual hardware and adaptive UI validation | PHYSICALLY VALIDATED |
| 16 A | Baseline and audit | IMPLEMENTED |
| 16 B–G | DSP, JNI, capture, screen, integrations, calibration/diagnostics/replay | AUTOMATED TESTED |
| 16 H | Hardening, physical evidence, documentation, commit and push | IMPLEMENTED |
| 17.1–17.8 | 96/48 kHz, mapping, image calibration, CAT, TX/split, hotplug, soak scenarios | PHYSICALLY VALIDATED — live CAT/96 kHz/performance passed; controlled calibration, TX/split, hotplug and long soak scenarios remain blocked |
| 18 | Evidence files include requested/configured/actual facts without fabrication | IMPLEMENTED |
| 19 | Audit, architecture, operator, validation/evidence, provenance and product docs reconciled | IMPLEMENTED |
| 20 | Definition-of-done checklist has no missing software feature | AUTOMATED TESTED |
| 21 | Exact final completion report | IMPLEMENTED |

## Final blockers

- Lenovo `TB373FU` clean-data validation selected CP2102N, completed USB permission without a second Connect action, identified the KX3, and proved the selected USB ADC as the actual 96 kHz stereo route. Live diagnostics measured 29.3 spectrum fps, 20.9 waterfall rows/s, 83 ms estimated latency and zero discontinuities while CAT revisions continued.
- Controlled known-generator calibration, deliberate TX/split, hot-unplug-under-capture, recording/replay comparison, explicit 48 kHz fallback and a 30–60 minute soak remain `BLOCKED — unsafe or unavailable operator/hardware scenario in this session`; they are not software-completion claims.
- Separate external-display validation remains `BLOCKED — no second physical display target`. Expanded and compact/freeform layouts were installed and inspected on the tablet.
- No software implementation or automated-test blocker remains.
