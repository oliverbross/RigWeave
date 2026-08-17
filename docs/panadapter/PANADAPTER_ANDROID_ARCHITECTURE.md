# Android KX3 panadapter architecture

## Scope and boundaries

This is the production Android receive-I/Q path for Elecraft KX3. It is independent of the mono audible monitor and the transmit-capable voice-macro path. It never creates playback, requests audio focus, opens CAT or network transports, or fabricates spectrum data. KX2 remains visible as an unsupported boundary because its wideband-I/Q readiness is not proven here.

## Data and ownership

```text
selected external USB input
  -> one AudioRecord (UNPROCESSED, stereo PCM16, 96 kHz or explicit 48 kHz fallback)
  -> PanadapterController capture thread
  -> one dedicated rw_panadapter_context through batched primitive-array JNI
  -> streaming complex DSP
  -> three preallocated snapshot buffers
  -> <=30 Hz coalesced Compose state
  -> Spectrum Canvas + circular Bitmap waterfall

existing CAT state -> effective RX/TX, split, RIT/XIT, mode and bandwidth
existing CAT sender <- explicit marker QSY only
DX snapshots       -> bounded read-only overlays
```

The controller owns the `AudioRecord`, native handle, capture/replay threads, route callback, waterfall bitmap, bounded recorder and calibration state. `close()` stops those resources and destroys the native handle. The shared audio arbiter pauses the audible monitor before panadapter acquisition and releases ownership on stop or failure.

## Route proof and failure behavior

The user selects a concrete external input. After `AudioRecord.startRecording()`, RigWeave compares the routed device with the requested device and records the actual client/device configuration, sample rate, channel count, encoding, source, session and buffer size. Mono, a rejected preferred route, an unexpected sample rate, route change, detach, repeated read failure, CAT disconnect, wrong radio model, or transmit state fails closed. A matching reattached device may recover automatically only when live operation was previously wanted.

## DSP contract

Left/right PCM enters as I/Q after explicit swap, polarity, conjugation and trim controls. Streaming IIR DC removal precedes an optional stable widely-linear correction `y = a*z + b*conj(z)`. Live frames never estimate or silently change that transform.

The DSP supports 1,024/2,048/4,096/8,192-point radix-2 FFTs; 25/50/75 percent overlap; Blackman-Harris, Hann, Nuttall, rectangular and flat-top windows; coherent-gain power normalization; ENBW/RBW reporting; independent linear-power averaging, attack/release trace smoothing and peak hold/decay. A robust lower percentile estimates the noise floor. Spectrum auto-level uses separate bounded attack/release against robust floor/peak statistics, while waterfall power averaging and line rate are independent controls. The optional generic Elecraft display curve interpolates bounded corrections at 0, 24, 48 and 96 kHz offsets without changing raw recordings.

High-resolution zoom is actual translate, 63-tap low-pass FIR and decimation by 2/4/8 before analysis. Sequence, input-frame, transform and discontinuity counters remain monotonic. A coherent native snapshot copies metadata, metrics, trace, waterfall powers and peak values from the same transform. The UI masks center bins only when explicitly requested; raw DSP bins remain available.

## Real-time and rendering policy

The capture thread uses preallocated short and native output arrays. FFT tables, windows, FIR state and work buffers are allocated at configuration time. UI publication is capped near 30 Hz, while all captured frames continue through DSP. The waterfall uses a fixed-size circular pixel buffer and power-aware bin aggregation rather than shifting history or inventing adjacent levels.

CAT center is accepted only from fresh connected KX3 state. Effective RX/TX incorporates VFO role and documented RIT/XIT offsets. Frequency gestures change only the local view; QSY requires an explicit marker action, rounds to the configured step, and enables Undo only after the requested frequency is observed in subsequent CAT state.

## Calibration and persistence

Settings use a versioned, validated private preference record and participate in the existing app backup/recovery scope. A calibration requires verified independent stereo, a stable unclipped off-center known tone and credible signal-to-floor margin. Coefficients are previewed, rejected for duplicated/weak/unstable/opposite-side/ambiguous input, and saved only after operator confirmation and credible image-rejection improvement. The profile is enabled only for the exact selected device fingerprint and configured sample rate.

## Privacy and support evidence

Raw I/Q recording is an explicit, bounded (maximum 60 second) private WAV plus metadata action. Replay feeds the production native path. Support export is a bounded private JSON snapshot containing route and DSP facts, Android/device model and CAT state; it contains no raw I/Q, credentials, database, logbook or network content.
