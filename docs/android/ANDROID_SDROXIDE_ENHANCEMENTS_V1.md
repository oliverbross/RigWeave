# Android SDRoxide Enhancement Pack v1

Status: source implemented on `feature/android-sdroxide-enhancements-v1`.

This pack adds an Android-native, receive-only TCI path; two visible receiver instruments; bounded I/Q and RX-audio routing; Panadapter/Waterfall v4 controls; receive-only scanning; band stacks; native RX DSP; RF map/globe views; Digi path context; and system-TTS announcements.

## Ownership

- `AndroidRadioPlatformController` remains the single radio backend owner.
- `PanadapterController` remains the single Panadapter owner and admits at most two TCI contexts.
- `AudioMonitorController` remains the audio lease owner; TCI RX audio uses `TCI_RX_AUDIO`.
- No TCI PTT, TUNE, TX audio, drive, or memory-write operation is exposed.
- Restore is disconnected, scanner stopped, streams stopped, and announcements disabled unless explicitly configured.

## Evidence boundary

Debug Lab is deterministic fake hardware and is labelled `DEMO · NO RADIO`. It proves UI, routing, lifecycle, and bounded processing only. Physical TCI, RF, audio quality, and TX remain separate acceptance layers.
