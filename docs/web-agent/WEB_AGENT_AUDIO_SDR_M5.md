# Web Agent audio and SDR M5

The Agent owns RX audio routes, Opus production, spectrum/waterfall frames, local virtual receivers, demodulation, scanner execution, recording/time shift/replay, signal measurement, trackers, channel monitors, calibration and survey collection. Browser display preferences never become hardware commands.

M5 bounds local receivers to two and channel monitors to four. Runtime commands are generation-checked, idempotent and lease-gated. Deterministic fake inputs may be used for tests; they are labelled `DEMO · NO RADIO` and are not evidence of physical audio, SDR, CAT or RF operation.

Recorded files and I/Q remain Agent-local. Web-visible bookmarks and metadata do not imply upload. Disconnect, local pre-emption, lease expiry and Global Stop terminate active receive runtimes safely.
