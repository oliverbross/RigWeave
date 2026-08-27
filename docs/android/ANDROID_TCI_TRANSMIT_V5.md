# Android TCI Transmit and Advanced Control v5

Android v5 adds one fail-closed TCI transmit authority beneath the existing radio platform owner. Digi, SSTV, reviewed CW audio-keying, immutable voice-macro plans, bounded Tune, and Global Stop submit typed intent; none can write `trx:true`, `tune:true`, or TX audio directly.

The production acceptance ladder is `UNVERIFIED`, `READ_ONLY_ACCEPTED`, `SAFE_SETTERS_ACCEPTED`, `TX_AUDIO_LOOPBACK_ACCEPTED`, `PTT_ACCEPTED`, `TUNE_ACCEPTED`, and `RF_ACCEPTED`. New, imported, restored, reconnected, or updated profiles never advance. A changed endpoint/reported-device identity resets acceptance. Debug acceptance is session-only.

The state machine is `RX_IDLE`, `TX_PREPARING`, `TX_ARMED`, `TX_ACTIVE`, `TUNE_ACTIVE`, `TX_STOPPING`, `RX_RECOVERY`, `RX_UNCONFIRMED`, and `FAULT`. Ambiguous preflight never advances. Ambiguous recovery latches `RX_UNCONFIRMED`, disables every TX source, and requires **REQUEST RX & RECHECK**.

The TCI cockpit exposes acceptance, RX/TX frequency truth, split/XIT, drive, tune drive, forward/peak power, SWR, protocol availability for reflected power/ALC/TX filter, local TX-audio RMS/peak/clipping, pacing, queue, interlock, PTT latency, and RX recovery. `TX AUDIO` is local audio, never RF spectrum.

Physical PTT, Tune, RF, audio-loopback, and radio-specific dialect acceptance remain pending until the controlled live checklist is performed. Source/fake evidence does not upgrade those states.
