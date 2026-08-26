# SDRoxide Android Feature Crosswalk

| Area | Android owner | Result |
|---|---|---|
| TCI WebSocket | `AndroidTciBackend` | Implemented, explicit connect, TLS validation, bounded frames |
| Multi receiver | `TciRuntimeState` / cockpit | Implemented, two simultaneous visible receivers, eight modeled |
| I/Q | `PanadapterController` | Implemented, two bounded native contexts |
| RX audio | `TciRxAudioController` | Implemented, explicit route lease and float AudioTrack |
| Panadapter/waterfall | `TciPanadapterPanel` | Implemented, dual view, FIT/manual, peak hold, palettes |
| Scanner | `ReceiveOnlyScannerController` | Implemented, memory/range/FFT, receive-only lifecycle stops |
| Band stacks | `BandStackStore` | Implemented, bounded persistent history and explicit recall |
| RX DSP | `NativeRxDsp` | Implemented, DC block, blanker, notch, NR, AGC hang, squelch, limiter |
| RF map | `RfObservationController` / Canvas | Implemented, filtered evidence and great-circle paths |
| RF globe | Compose orthographic projection | Implemented, pan/zoom, horizon clipping, control points |
| Digi/WSPR path | `DigiRfPathWrapper` | Implemented as evidence context, never RF proof |
| Spoken announcements | `SpokenAnnouncementController` | Implemented with system TTS and safety suppression |

Totals: 12 source-complete, 0 settings-only, 0 desktop-only. Live/physical acceptance remains explicitly separate.
