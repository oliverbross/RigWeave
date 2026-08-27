# Android TCI Control v2

TCI mutations address an explicit receiver where the protocol requires it. VFO, IF offset, mode, RX enable/mute, split, global volume, IQ/audio stream commands and stable telemetry are decoded into one runtime snapshot.

Rapid safe setters use a generation-scoped latest-write-wins queue. A disconnect clears queued and pending writes; nothing replays after reconnect. Confirmed readbacks and failed writes are visible in the workbench and Health.

RIT/XIT and passband setters remain unavailable because no stable audited contract was found. Spot exchange is `UNAVAILABLE_PROTOCOL`. Android TCI contains no PTT, TUNE, TX-audio, or automatic reconnect/stream restore path.
