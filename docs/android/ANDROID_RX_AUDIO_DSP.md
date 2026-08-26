# Android RX Audio DSP

TCI RX audio uses one explicit `TCI_RX_AUDIO` lease from `AudioMonitorController`, a bounded eight-frame queue, mono float output, and the selected built-in speaker route. Starting another receiver/rate stops the old path; route loss, background, disconnect, profile change, Global Stop, and close release the route.

Native DSP provides a DC blocker, impulse blanker, 1 kHz adaptive-style notch path, noise-reduction smoothing, AGC with bounded hang, squelch, output gain, and a soft limiter. Health exposes input/output level, squelch state, clipping, and dropped frames without retaining raw audio.

DSP controls are receive-only. TCI RX audio is never connected to voice-macro or radio TX routing.
