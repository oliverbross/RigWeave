# Nexus and AetherSDR upstream record

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
| `crates/tempo-net/src/flexvita.rs` at commit `6ec4a7925f1550cc364c7fd95967ce38c696ad3f` | Flex OUI/class framing, big-endian VITA envelope parsing, FFT/meter/audio dispatch concepts | `src/vita.rs` | Reimplemented as a bounded reusable core; honours declared size, optional timestamps/trailer and exact-byte Opus packets; adds stream registry, sequence accounting, coverage-based FFT/waterfall assembly, radio-y-pixel dBm conversion, meter units, audio classes, UDP policy and fail-closed ownership | Standard library only; focused host tests pass |

## AetherSDR behaviour adaptation

Selected GPL-3.0-only behaviour was adapted from [AetherSDR](https://github.com/AetherSDR/AetherSDR) at immutable commit `5a499ef6a436625fab353727e462b92d7436118e`.

- Upstream copyright: AetherSDR contributors, as recorded by the upstream files and repository history.
- Upstream licence: GPL-3.0-only.
- Local paths: `src/vita.rs` and Android `FlexVita.kt`, `FlexAudio.kt`, `FlexMicTx.kt`, `FlexStreamSession.kt`, `FlexControl.kt`, `SmartLink.kt`.
- Adapted behaviour: unique-bin FFT/waterfall coverage, 36-byte waterfall metadata, radio-`ypixels` FFT scaling, unpadded Opus VITA payloads with rounded word count, 24 kHz stereo/10 ms Opus framing, bounded TX pacing, packet-loss handling concepts, WAN UDP register-until-first-VITA then ping, scoped self-signed-radio TLS with TOFU pinning, owned-object removal, and TX cleanup policy.
- Not copied: Qt/QML UI, application architecture, audio DSP chain, settings framework, automation, proprietary assets, tests unrelated to these behaviours, or generic command surfaces.
- Endorsement: AetherSDR does not endorse RigWeave.

The local files are adapted derivatives, not verbatim snapshots. The cited upstream records identify reused source regions or behaviour; other local lines are RigWeave additions. `src/ffi.rs`, `include/rigweave_flex.h`, SmartLink Auth0/broker orchestration and the Compose cockpit are new RigWeave code.

## Explicitly not imported

- `crates/tempo-audio/src/flexspectrum.rs` — not imported; its desktop audio/runtime coupling is not suitable for this Android control runtime.
- `crates/tempo-audio/src/flexdax.rs` — rejected wholesale; its unconditional DAX TX route and modem coupling were not imported.
- Nexus Tauri/React UI, `src-tauri`, `tempo-app`, `tempo-audio`, Hamlib, DSP/digital-mode crates, release scripts and unrelated features.

The dependency lock is committed at `Cargo.lock`; generated libraries and Cargo targets are excluded from source control.
