# Android KX3 panadapter validation evidence

Evidence date: 2026-08-17 (Australia/Darwin). Branch: `codex/android-kx3-panadapter`. Baseline: `5d512a91e58f4a7746193aec44fbc71f0efdfa96`.

## Automated evidence

| Layer | Command / fact | Result |
|---|---|---|
| Shared DSP | SDK CMake build plus focused CTest | PASS; 1/1 test target, including expanded panadapter cases |
| Android domain | `:app:testDebugUnitTest` | PASS; settings round-trip/recovery, route proof, passband, frequency rounding and reframe behavior |
| Android package | `:app:assembleDebug` | PASS; debug APK built for configured ABIs |
| JNI on device | `:app:connectedDebugAndroidTest` | PASS; 7/7 tests on Lenovo `TB373FU`, including dedicated panadapter native lifecycle/config/push/snapshot |
| Android lint | `:app:lintDebug` | BASELINE FAILURE; 9 errors/50 warnings/17 hints, with no error in a panadapter file. Errors are pre-existing EQ/voice permission, API-level, Neural DX, and unrelated `MainActivity` findings; this task does not baseline or broaden scope to hide them. |

The shared tests cover supported FFT sizes, deterministic complex-bin orientation, amplitude normalization, ENBW/RBW/overlap metadata, stable fixed image correction, true translate/filter/decimate zoom and coherent C-ABI frame copies. The connected test creates/configures/destroys a dedicated native context and verifies a deterministic complex tone through push/snapshot.

## Physical evidence

The debug APK installed and cold-launched successfully on Lenovo `TB373FU`, ADB serial `adb-HA248BS3-Vsaw6A._adb-tls-connect._tcp`. Expanded navigation and the no-scroll offline instrument were visually inspected. A bounded freeform resize also exercised the compact six-destination navigation and `Controls | Panadapter` subview; its semantics exposed all status and action labels without a crash.

Installed artifact: `android/app/build/outputs/apk/debug/app-debug.apk`, version `0.1.0` (`versionCode 1`), 89,775,779 bytes, SHA-256 `4886cd7d99a24db5318e7764c0ff70b39e20cd123c8115fcec8f8f765efe9b8a`.

Android enumerated `USB Advanced Audio Device` (`0d8c:8810`, card 2/device 0) as a two-channel input advertising 96 and 48 kHz plus PCM encodings 2/4/21. The setup UI displayed those actual capabilities and allowed explicit selection. It also enumerated CP2102N (`10c4:ea60`) and Prolific (`067b:23a3`) serial adapters. The Prolific adapter opened but correctly failed identification because it returned no Elecraft response. A clean-data selection of CP2102N persisted immediately, the USB permission dialog completed without a second Connect action, and the app reached `CAT LIVE`, identified `KX3`, and reported active FA/FB state.

With CAT live, Panadapter Start proved the requested and actual route were the same `USB Advanced Audio Device`, `AudioSource.UNPROCESSED` (9), stereo mask `0xC`, PCM16 encoding 2, and 96,000 Hz. The live diagnostics reported 4,096 FFT, 2,048 hop, 46.99 Hz RBW, zero discontinuities, independent-channel RMS/correlation, 29.3 published spectrum fps, 20.9 waterfall rows/s, and an 83 ms capture/display estimate while CAT revisions continued. Android gfx measurement after the rendering repair reported 1,670 frames, 26 janky (1.56%), 25/29/31/36 ms at p50/p90/p95/p99, one slow bitmap upload, and 13 slow draw-command frames; steady PSS/RSS were about 345/467 MB and thermal status remained 0. The settings inspector opened during live capture, and the action/calibration strip remained visible above the tablet taskbar.

## Required live scenarios

- `PASS`: installed/cold-launched APK; 7/7 connected tests; clean CP2102N selection/permission/persistence; identified live KX3 CAT; exact external stereo 96 kHz route proof; live spectrum/waterfall; zero observed discontinuities; required 25/20 cadence targets; sub-200 ms estimate; responsive live inspector/action strip; compact and expanded layouts.
- `BLOCKED — requires controlled operator/hardware scenario`: explicit 48 kHz fallback, known calibrated generator frequency/sign/amplitude, measured image/level/flatness calibration improvement, deliberate split/TX freeze, marker QSY/observed undo, hot-unplug recovery, bounded recording/replay comparison, and a 30–60 minute soak were not safely exercised in this session.
- `BLOCKED — no second physical display target`: separate external-monitor layout validation. Tablet freeform resizing proves the compact composition path but is not a second-display claim.

## Evidence interpretation

Automated synthetic input is DSP/JNI evidence only. A software launch is UI/lifecycle evidence only. Physical acceptance requires the actual KX3 CAT connection, a proven independent stereo USB route and a known live receive-I/Q signal. The final implementation-audit matrix and completion report are authoritative after the last validation run.
