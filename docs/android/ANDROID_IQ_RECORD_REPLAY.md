# Android I/Q Recording and Replay

`IqCaptureRepository` records explicit receive-only complex samples as interleaved little-endian float32 (`I,Q,I,Q…`) in `.f32iq`, with a UTF-8 JSON sidecar carrying format/version, UTC epoch, source, receiver, center frequency, sample rate, profile, band, context, note and complex-frame count. Files live only under app-private `files/sdr/iq-captures`.

Data and metadata are written to `.tmp`, flushed with `fd.sync()`, then atomically renamed. Startup removes incomplete temporary files. Default limits are ten minutes per file and 2 GiB total; limits can only be lowered/raised inside the hard bounds. Oldest completed pairs are removed when the total cap is exceeded. Starting, stopping and deleting are explicit; QSO storage is never involved.

`ReplayIqSource` opens the completed file locally and emits a first-class `REPLAY` I/Q source. It supports play/pause, timeline seek, ±10-second skip and 0.25/0.5/1/2×. At 1× replay can feed the existing receive analysis/audio chain; at every other speed audio is disabled and labelled as such. Selecting replay stops active local receive audio, sends TCI `iq_stop`, detaches panadapter TCI contexts, and suppresses late live frames. `RETURN LIVE` does not silently restart a stream: explicit reattachment is required.

Replay never changes a physical VFO or authorizes CAT/PTT/TUNE/TX. Capture/replay status, bytes, duration, speed and truth are visible in the Panadapter, Settings and Health.
