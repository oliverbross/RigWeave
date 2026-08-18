# Nexus upstream record

`rigweave-flex` is GPL-3.0-only source derived from selected Flex protocol code in [kd9taw/Nexus](https://github.com/kd9taw/Nexus).

- Immutable upstream commit: `6ec4a7925f1550cc364c7fd95967ce38c696ad3f` (2026-08-17)
- Upstream copyright: Copyright (C) 2026 KD9TAW `<kd9taw@protonmail.com>`
- Upstream licence: GPL-3.0-only
- RigWeave modifications copyright: Copyright (C) 2026 Oliver Bross
- Local licence: GPL-3.0-only; repository `COPYING` supplies the licence text
- Endorsement: neither KD9TAW nor Nexus endorses RigWeave

## Imported and adapted source

| Upstream path and relevant lines | Imported symbols/behaviour | Local path | Modifications | Dependency and test status |
|---|---|---|---|---|
| `crates/tempo-net/src/flexdisc.rs` lines 1-116 | `FlexRadio`, `parse_discovery`, reusable UDP-4992 socket, bounded discovery and its two tests | `src/flexdisc.rs` | Renamed/expanded record; retained model, nickname and IP and added port, serial, callsign, version, status and GUI metadata; deduplicate by serial; 4 KiB bound; reusable/broadcast socket; no fake payload in production | `socket2 0.6` only; host tests and four Android ABI builds pass |
| `crates/tempo-net/src/flexcat.rs` lines 1-101, 153-199, 226-227, 289-314 and 375-398 | `FlexMsg`, V/H/R/S/M parsing, slice status parsing, command framing and focused regression tests | `src/flexcat.rs` | Renamed and extended into incremental bounded framing, complete radio/client/station/slice state, removal handling, stable selection and explicit receive-only builders; removed panadapter, meter, DAX and stream-create models and all broad command entry points | Standard library only; host parser/state/safety tests pass |

The local files are adapted derivatives, not verbatim snapshots. The cited upstream ranges identify every reused source region; other local lines are RigWeave additions. `src/ffi.rs`, `include/rigweave_flex.h`, SmartLink authentication/broker/TLS code and Android orchestration/UI are new RigWeave code.

## Explicitly not imported

- `crates/tempo-net/src/flexvita.rs` — Phase 5B candidate for VITA FFT/meters.
- `crates/tempo-audio/src/flexspectrum.rs` — Phase 5B candidate, not suitable for this Android control runtime.
- `crates/tempo-audio/src/flexdax.rs` — rejected wholesale; it includes TX/DAX orchestration. Any Phase 5C work must separate and re-audit RX-only audio.
- Nexus Tauri/React UI, `src-tauri`, `tempo-app`, `tempo-audio`, Hamlib, DSP/digital-mode crates, release scripts and unrelated features.

The dependency lock is committed at `Cargo.lock`; generated libraries and Cargo targets are excluded from source control.

