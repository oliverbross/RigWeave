# Android KX3 panadapter validation evidence

## RF-correctness remediation — 2026-08-17

Commit `fb86c523905bb4c1323bc878ab68380a333c90e7` is the remediation baseline. Its USB-route, client-capture, JNI, rendering-cadence, responsiveness, and CAT-coexistence measurements remain valid and are retained below as `ROUTE/PERFORMANCE VALIDATED` evidence.

`RF/IQ/DISPLAY ACCEPTANCE FAILED — software remediated; operator RF proof still STOPPED.` The supplied live photograph showed a central saturated yellow/green block with sharp bandwidth shoulders, regularly spaced comb spurs, and no useful weak-signal discrimination. That is a substantive RF-correctness failure, not a minor cosmetic defect. The prior session did not prove the physical device-format rate, true 96 kHz bandwidth, I/Q orientation, useful image rejection, calibrated level/flatness, or display fitness. Any earlier `PHYSICALLY VALIDATED` wording implying those facts is narrowed accordingly; the earlier route and performance measurements are not deleted.

### Remediation device evidence

- Hardware: Lenovo `TB373FU`; KX3 CAT live through CP2102N; StarTech ICUSBAUDIO2D / `USB Advanced Audio Device` (`0d8c:8810`) with KX3 I/Q connected to the stereo microphone input.
- Direct 96 attempt: Android active client and device formats both reported PCM16, stereo mask `0xC`, 96,000 Hz, `UNPROCESSED`, selected USB route, no client/device conversion and no effects. Repeated display snapshots found `46.9–49.7%` of bins useful (about `45.0–47.7 kHz`), a stabilized valid-band floor of about `-87.2 dBFS`, waterfall black/top `-85.8/-37.7 dBFS`, `0%` saturated bins, and a `1,031.25 Hz` comb at `100%` persistence. Android therefore did not prove the suspected framework 48-to-96 resampling; the unresolved half-width response is below or outside that typed framework boundary and 96 kHz remains unaccepted for RF-wide operation.
- Direct 48 comparison: Android active client and USB HAL both reported PCM16 stereo at 48,000 Hz. The final analyzer snapshot found `95.8%` valid bins (`46.0 kHz`), `0%` saturation, `-90.8 dBFS` stabilized floor, waterfall black/top `-89.2/-41.2 dBFS`, 28.8 spectrum fps, 23.9 waterfall rows/s and 83 ms estimated latency. This is the preferred honest operating mode for the present path.
- Retained 10-second production WAV comparison: `evidence/iq-48k.wav` is 480,000 stereo frames and `evidence/iq-96k.wav` is 960,000 stereo frames. Reproducible 4,096-point 50%-overlap long-term averaging found the 48 kHz capture useful across the full nominal width, while the direct 96 kHz capture crossed the broad-envelope `-18 dB` boundary at about `47.30 kHz` total width (`49.27%`) with `47.83 dB` middle-to-outer-edge power contrast. See `evidence/long-term-spectrum-comparison.png`, `evidence/long-term-spectrum-metrics.json`, and `tools/analyze_panadapter_iq.py`.
- Raw-sample diagnosis confirms that the comb precedes the FFT and renderer. The retained 48 kHz PCM has strong components near `±1`, `±2`, `±3`, and `±4 kHz` and complex-sample correlation at the 48-sample/1 kHz period; the 96 kHz PCM repeats the same frequency pattern and correlation at 96 samples. Swapping rate does not move the comb in hertz. This rules out overlapping display buckets as its source, but it does not distinguish sound-card/platform coupling from the connected radio/cable/environment without the safe A/B/C physical captures.
- Alternate ADC diagnostic: Creative Sound Blaster Play! 3 (`041e:324d`) advertised two-channel PCM16 at 48 kHz and Android proved a direct two-channel route, but the retained channels are sample-for-sample identical (`I/Q correlation 1.000`), confirming a mono capture duplicated to stereo rather than quadrature I/Q. Its 1 kHz-period autocorrelation was `0.109`, versus `0.301` for the StarTech 48 kHz capture, and the StarTech's exact 1 kHz harmonic family was not reproduced. The alternate path instead contained different strong low-frequency/mains-related and 2/8/12 kHz artifacts, with both channels at about `-54.45 dBFS`; it is neither a valid I/Q replacement nor a clean baseline. See `evidence/soundblaster-48k.wav`, `evidence/soundblaster-48k.json`, `evidence/soundblaster-48k-live.png`, `evidence/input-card-comparison.png`, and `evidence/input-card-comparison-metrics.json`.
- Physical mic/IQ unplug/replug retained direct 48 kHz stereo but changed observed channel RMS from about `-94.3` to `-96.9 dBFS` (about 2.6 dB). That demonstrates why level/flatness/IQ calibration is invalidated on physical route change.
- Rate controls are always visible as **48K** and **96K**. Each live change closes the current stream, opens the requested rate, and repeats typed client/device/route proof. Nominal and usable bandwidth are separate UI values.
- Final debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`, 87,454,129 bytes, SHA-256 `bd5f9aa45334d8218e22b33344594daaa37d598498c0562d5b9c233602bc6a58`. It installed with `adb install -r`; app data and the working CP2102N CAT selection were preserved. Final focused validation passed 81 Android unit tests, 7/7 connected tests on the Lenovo, and the shared-core CTest target.
- Before evidence is the owner-supplied photograph retained as a conversation artifact. Repository after captures: `docs/panadapter/final-48k-live.png`, `docs/panadapter/final-96k-live.png`, `docs/panadapter/final-48k-diagnostics.png`, `docs/panadapter/final-96k-diagnostics.png`, `docs/panadapter/evidence/long-term-spectrum-comparison.png`, and `docs/panadapter/evidence/input-card-comparison.png`.

The StarTech product documentation states that the three hardware LEDs show playback sample rate and that the microphone input is stereo: <https://www.startech.com/en-us/cards-adapters/icusbaudio2d>. The Elecraft KX3 manual describes approximately ±24 kHz at 48 ksps and ±48 kHz at 96 ksps; its documented analogue roll-off is only several decibels, so it does not explain a sharp tens-of-decibels half-width cutoff: <https://ftp.elecraft.com/KX3/Manuals%20Downloads/archive/E740163%20KX3%20Owner%27s%20man%20Rev%20C4.pdf>.

### Acceptance status

- `PASS`: typed direct-format negotiation and fail-closed fallback; visible 48/96 selection; valid-bin masking; robust independent display levels; non-overlap reduction; periodic-spur warning; route-loss calibration invalidation; CAT coexistence. The 48 kHz display now exposes the usable span and noise-floor variation without broad palette saturation, but the physical chain is not RF-accepted while its 1 kHz comb remains unresolved.
- `STOPPED — operator action required`: two known stable opposite-offset tones have not been supplied, so frequency-axis orientation and image rejection are not proved and the app correctly displays `I/Q CHANNELS HEALTHY ORIENTATION UNVERIFIED`.
- `STOPPED — operator action required`: spur captures A/B/C require physically safe terminated/no-antenna/normal-station configurations. No stage was labelled without that physical setup.
- `STOPPED`: the 96 kHz device interface is direct according to Android, but its approximately 48 kHz useful response remains unexplained and uncalibrated. The UI masks and labels the limitation instead of presenting the full nominal span as useful RF.

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
