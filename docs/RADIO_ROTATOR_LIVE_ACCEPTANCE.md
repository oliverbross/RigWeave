# Radio and rotator live acceptance

## Evidence layers

| Layer | Status | Evidence required |
|---|---|---|
| Source and semantic integration | Implemented | Exact source SHAs, merge ledger and focused tests. |
| Android/native build | Local pass; hosted pending | APK/AAB audit, 688 JVM tests, 97 Rust tests, 2/2 CTests and unsigned iOS Simulator build passed locally. Exact-SHA hosted gates remain pending. |
| Protected tablet preservation/install | Pending | `pm path`, hash-recorded backup, `adb install -r`, package/data checks. |
| QMX CAT identity and readback | Pending hardware | Owner-present, read-only connect and fresh readbacks. |
| QMX UAC/IQ orientation | Pending hardware | Same-device route proof, 48 kHz stereo evidence and orientation review. |
| RGO ONE V6 | Pending hardware | Explicit V6 profile, model ID 006 and fresh read-only values. |
| ARCO/rotator | Pending hardware | Published compatibility mode, read-only position, stationary STOP, limits and offset review. |
| Audio, PTT, TUNE, RF and movement | Not authorized by build/install | Separate explicit operator confirmation and observed evidence. |

## Owner-present sequence

1. Verify no transmitter or rotator automation is armed.
2. Confirm the exact device identity and physical cabling.
3. Use the read-only test path first; do not press PTT, TUNE, park or move.
4. Compare displayed state with the physical controller/radio.
5. For rotators, verify limits, wrap/cable path and heading-offset ownership before any separately confirmed motion.
6. Record failures as unavailable/unknown; do not infer success from a build, install or log message.
