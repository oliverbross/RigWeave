# Android Local SDR Live Acceptance

Source, local tests, hosted builds, protected-device process, unlocked visual, physical I/Q, physical audio and live RF are separate evidence layers.

Pending live checks require an authorised compatible source and receive-only setup:

- USB/LSB/DIGU/DIGL opposite-sideband quality and frequency accuracy;
- CW pitch/filter audio, AM depth and SAM acquisition/fallback under drift;
- NFM squelch, all CTCSS tones and normal/inverted DCS false-positive behavior;
- WFM source bandwidth, mono fallback, stereo separation and multipath behavior;
- RDS PI/PS/PTY/TP/TA/RadioText/AF/clock and block-error behavior on real stations;
- dual receiver latency, route loss, mixing, underrun and long soak;
- explicit recording, pre-roll, quota/retention, secure share/delete and Scanner audio hit;
- unlocked tablet navigation and accessibility.

Debug SDR Lab v3 proves deterministic DSP/UI/lifecycle only. Every screenshot must show `DEMO · NO RADIO`. No physical transmit, PTT, TUNE, TX audio, RF or rotator action is part of this programme.
