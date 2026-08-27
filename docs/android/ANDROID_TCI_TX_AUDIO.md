# Android TCI TX Audio

The native boundary accepts bounded mono finite floats plus source/target rates, exact target-frame offset, requested stereo value count, receiver, and one per-mode level. It validates receiver 0..7, source rate, audited target rate (8/12/24/48 kHz), even value count 1..16384, finite samples, and level 0..2. Linear resampling uses one continuous target timeline; the hard limiter is ±0.98. Output is a 64-byte little-endian TCI header plus FLOAT32 interleaved stereo.

Chrono requests are exact. A bounded queue holds eight requests; overflow drops the oldest and accounts an overrun. Missing chrono pacing uses a finite 2048-value fallback and accounts an underrun. Every frame, queue depth, jitter, RMS, peak, clipping, PTT latency, and RX recovery latency is measured. Encoding and network pacing run off the main thread. Raw TX audio and raw TX frames are excluded from logs and support bundles.

FT8/FT4 timing remains with Digi. Voice plans keep immutable clip ordering, bounded silence, and the 45-second ceiling. CW uses the reviewed audio-keyed encoder. SSTV uses the existing encoder. Preview never submits a TX intent.
