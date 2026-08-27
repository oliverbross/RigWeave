# Android SDR Operator Workbench v4

Android v4 extends the existing `PanadapterController`, `SdrOperationalV2`, `LocalReceiverController`, `ReceiveOnlyScannerController`, `AndroidTciBackend`, RF-observation and band-stack owners. `AndroidSdrWorkbenchV4` is the single coordinator for new analysis-only state; it never owns CAT, PTT, TUNE, TX audio or a second Android audio output.

The operator surface adds production float32 I/Q capture and offline replay, historical truth labels, Marker A/B measurements, a signal tracker with optional local-receiver-only follow, a bounded Spectrum Survey, history-aware scanner ordering, four lightweight channel monitors, receive calibration, memory import/export, Panadapter v6 tools, and Settings/Health visibility. Generic proven stereo I/Q and TCI use the same capture/replay repository.

## Safety and truth

- Capture is explicit, app-private and capped; temporary files are recovered after interruption.
- Replay is labelled `REPLAY · OFFLINE`, does not move a physical VFO, stops receive audio on selection, explicitly detaches TCI I/Q, and rejects late live frames until the operator reattaches.
- Audio is truthful only at 1× replay. Other speeds publish `AUDIO DISABLED AT REPLAY SPEED`.
- Measurements remain `dBFS · RELATIVE` until the operator saves a source-specific calibration.
- Historical occupancy is never presented as current RF. Debug fixtures always say `DEMO · NO RADIO` and never enter the persistent survey.
- Tracker follow may change only the offset of an existing local virtual receiver and fails closed outside the current I/Q span.

Production defaults are a ten-minute file cap, 2 GiB total capture cap, 30-day Spectrum Survey retention, 15-minute/1 kHz aggregation buckets, 250,000 rows and 64 MiB. Scanner adaptation is off by default and never shortens the operator dwell.

Source/build, hosted, protected-device process, unlocked visual, authenticated service, audio, CAT/PTT/TUNE, RF, calibration and release evidence remain separate.
