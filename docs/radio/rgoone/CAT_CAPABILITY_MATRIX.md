# RGO ONE CAT Capability Matrix

Authority: official RGO ONE V6 CAT manual v1.03 for firmware 1.08 and higher,
with firmware 1.09 release notes for corrected memory operation.

| Area | Commands | Core treatment | Action class |
|---|---|---|---|
| Framing | semicolon terminator | bounded ASCII decoder, oversize discard/recovery | READ_ONLY |
| VFO | `FA`, `FB`, `FR`, `FT`, `FS` | exact read/set builders and typed responses | READ_ONLY / SAFE_SET |
| Mode/status | `MD`, `IF`, `AI` | typed mode; bounded raw IF/AI where layout is delegated | READ_ONLY / SAFE_SET |
| AGC/gain | `GT`, `RG`, `PA`, `RA` | exact typed values | READ_ONLY / SAFE_SET |
| RIT/XIT | `RT`, `XT`, `RC`, `RD`, `RU` | toggles plus single-shot clear/nudge; no invented direct offset setter | SAFE_SET / EDGE_TRIGGERED |
| Meters | `SM`, `RM` | S, RF power, ALC, SWR, COMP values 0-15 | READ_ONLY / SAFE_SET |
| Power/audio | `PC`, `MG`, `ML`, `PL`, `VD`, `VG` | documented values; PL retained bounded because module presence is not proven by support | READ_ONLY / SAFE_SET |
| Keyer | `KS`, `KY`, `SD`, `PB` | KS typed; KY/PB bounded; no separate keyer owner added | READ_ONLY / SAFE_SET / EDGE_TRIGGERED |
| Modules | `AC`, `NB`, `NL`, `EX` | ATU/NB/AF presence only on positive documented evidence | READ_ONLY / SAFE_SET / TUNE |
| Bands | `BD`, `BU`, `EX079` | typed edge contract; no local band-plan authority | EDGE_TRIGGERED |
| Identity | `ID`, `FW`, `SN` | model 006 and firmware typed; SN hashed immediately | READ_ONLY |
| Memory | `MC`, `MR`, `MW` | firmware 1.08+ gated; 1.09 is reviewed correction; write default off | READ_ONLY / EDGE_TRIGGERED / MEMORY_WRITE |
| TX/RX | `RX`, `TX0`, `TX2` | single-shot reviewed actions, never polling/retry | EDGE_TRIGGERED / TRANSMIT / TUNE |
| Lock | `LK` | bounded documented command, not automatically polled | SAFE_SET |
| Service unlock | `UN` | deliberately has no builder, action, or UI | FORBIDDEN |

## Generation matrix

| Generation | Identity | Reads | Writes |
|---|---|---|---|
| V6 confirmed | operator/USB evidence, then `ID006` and `FW` | documented v1.03 commands | only after write confirmation and safety review |
| Series 5/5+ | manual profile only; no current exact ID command evidence | command capabilities remain unknown pending official/captured evidence | disabled |
| Unknown | no V6-only wire probe | metadata-only conservative profile | disabled |

## Requested controls without exact command evidence

Filter selection/width, AF/APF/NR/notch levels, band list, XFC as a distinct state,
direct RIT/XIT offsets, USB-audio format, transverter/RX-antenna control, and a complete
module absence probe remain unknown. The UI model can display independently supplied
truth, but the protocol builder does not fabricate commands for these items.
