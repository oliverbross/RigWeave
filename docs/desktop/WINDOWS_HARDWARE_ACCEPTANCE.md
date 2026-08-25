# Windows Hardware Acceptance

This checklist is intentionally unclaimed at the branch tip.

Before a hardware row can pass, record the exact Windows package SHA, machine/OS, device firmware, transport, capability snapshot, before/after readback, Stop behavior and teardown. Transmit, tune, direct-tone and movement require a separate explicit approval and safe test setup.

| Hardware path | Receive/source evidence | Physical acceptance |
|---|---|---|
| Hamlib generic radio | source and hosted build expected | Pending |
| KX3/KX2 native | profile surface only | Pending; native controller not bound |
| FlexRadio LAN/WAN | profile surface only | Pending; native controller not bound |
| QMX/QMX+ | profile surface only | Pending; native controller not bound |
| RGO ONE | profile surface only | Pending; native controller not bound |
| Hamlib rotator | source controller present | Pending |
| GS-232/EasyComm/DCU/SPID/rotctld | protocol surface only | Pending; controllers not bound |
| Panadapter/audio | source receive controller present | Pending |
| Keyer/PTT/TUNE/direct tone | unavailable without capability | Not authorised; pending |

Unknown commands remain unknown. No unsupported command may be guessed to complete this table.
