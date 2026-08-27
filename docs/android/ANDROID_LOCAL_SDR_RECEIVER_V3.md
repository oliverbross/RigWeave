# Android Local SDR Receiver v3

Status: source-complete receive-only candidate on `feature/android-local-sdr-receiver-v3`, based on exact v2 SHA `398ec9380a1ce72b5b561f51bb33af7effc6ff97`.

## Delivered contract

- One application-scoped `LocalReceiverController` consumes the existing Panadapter I/Q path and owns at most two virtual receivers.
- One shared C++17 `LocalReceiverDsp` performs bounded NCO translation, complex channel filtering, rate conversion, demodulation and metadata decoding.
- USB, LSB, CW, DIGU, DIGL, DSB, AM, SAM, NFM and SPECTRUM operate from 48/96/192 kHz compatible sources. WFM stereo and RDS are capability-gated to at least 192 kHz.
- SAM reports ACQUIRING, LOCKED or FALLBACK from its PLL state. CTCSS and DCS expose confidence; RDS exposes only CRC-validated group data.
- Demodulated audio enters the existing `TciRxAudioController`, preserving its one AudioTrack, two eight-frame queues, mixer, existing receive DSP, limiter, audio focus and route-loss behavior.
- Panadapter tap creates an explicit Add/Move Local RX review. Local markers and passbands move only the NCO; out-of-span choices produce `OUTSIDE CURRENT I/Q SPAN` and require a separate physical receive review.
- Explicit PCM16 WAV recording is app-private, one-at-a-time, at most 30 minutes, with a 250 MB maximum total cap, atomic finalisation, sidecar metadata and operator-visible state.
- Scanner AUDIO record-on-hit requires per-bank enablement and an already listening local receiver. It never records by default or silently.
- Debug SDR Lab v3 is `BuildConfig.DEBUG` only, labelled `DEMO · NO RADIO`, and generates bounded signals at runtime.

## Safety and restore

The local receiver cannot dispatch PTT, TUNE, TX audio, drive, tone or keying actions. Global Stop, background/profile/disconnect cleanup and close stop listening, recording, Scanner capture and playback. Only safe per-mode preferences restore; receiver instances, offsets, listening, recording, acquisition and metadata sessions do not.

## Evidence ledger

1. Frozen v2/v1/RC1 and protected refs were verified before worktree creation.
2. SDRoxide v1.5.3 remains the current stable audit pin; no source or payload is incorporated.
3. Shared core owns the demodulator; Android owns lifecycle, storage and presentation.
4. Two local receivers share one input queue and one controlled DSP worker.
5. Every input queue, output queue, recording, text field, metadata list and database query is bounded.
6. Native golden vectors cover sideband rejection, CW, AM/SAM, NFM, all 50 CTCSS tones, all 104 standard DCS codes in normal/inverted form, WFM stereo, CRC-gated RDS PI/PTY/TP/TA/PS/RadioText/AF/clock assembly, Spectrum silence and churn.
7. JVM tests cover capabilities, defaults, bounds, safe restore, WAV finalisation and every debug fixture; the final local source gate passed 736 tests with no failures or skips.
8. Instrumentation sources exercise the JNI lifecycle and all receive modes without touching the protected tablet.
9. Normal, AddressSanitizer and UndefinedBehaviorSanitizer core gates each passed all 6 CTests. Android lint, instrumentation packaging, four-ABI AAB and arm64 APK passed their local gates; final sizes and hashes are generated only after the immutable candidate SHA exists.
10. Apple Fast Entry and unsigned generic iOS Simulator/device builds passed. The local macOS desktop configure remains environment-limited by the absent Qt 6.11.2 TaskTree module; exact-SHA hosted macOS/Windows jobs remain authoritative.
11. Hosted, protected-device and live-RF results are recorded only after their separate gates.

Physical RF, tablet speaker quality, real-station RDS/DCS performance and protected-tablet visual acceptance remain distinct from source/build evidence.
