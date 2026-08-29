# Android 1.0 SDRoxide watcher review

## Decision

`v1.5.4` is **EXPECTED_REVIEWED** as a read-only upstream reference. The RigWeave watcher pin and provenance ledger are updated; no upstream code, asset, vendor subtree, dependency, model, recording, fixture, or behavior is imported. `PACKAGE_UPDATE_REQUIRED` and `UNEXPECTED_BLOCKER` both have zero entries.

## Immutable identity

| Field | Previously reviewed | Current reviewed |
|---|---|---|
| Release | `v1.5.3` | `v1.5.4` |
| Commit | `a680935b10f33768a499435e8bd37f779fa640ae` | `1f62978036aaa0e3e9f80bca5db4c19102962fd7` |
| Tree | `4697195080495da4a727b14234b85af89c10ecda` | `77a8a562e7c44d7cc9a77cec3169aeba13bc83d3` |
| Repository licence SHA-256 | `3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986` | unchanged |

GitHub reports 12 commits, 127 changed paths, 38 additions, and 89 modifications. The release tag, commit, tree, repository licence digest, comparison range, and every changed path were verified through the upstream GitHub repository at the immutable commit.

## Review classification

- `EXPECTED_REVIEWED`: source, test, workflow, documentation, and asset changes inspected as upstream behavior/reference only.
- `PROVENANCE_UPDATE_REQUIRED`: upstream licence, notice, or provenance records that must be acknowledged in this ledger.
- `PACKAGE_UPDATE_REQUIRED`: none; RigWeave packages no SDRoxide v1.5.4 path.
- `UNEXPECTED_BLOCKER`: none; the repository licence digest is unchanged and no upstream material is incorporated.

Disposition totals: `121` expected-reviewed and `6` provenance-update-required.

## Vendored additions reviewed

The upstream release adds or updates vendor records, but none is copied into RigWeave:

- `vendor/dream/PROVENANCE.md` SHA-256 `165dff942fc5b559637f728b89854145188f18914b1373b57d37198f3ad2574c`; Dream 2.2, GPL-2.0-or-later.
- `vendor/ewebsock/PROVENANCE.md` SHA-256 `259e942abef1d21f3c71d480bd28af98638c4f4be170992e7e2d052cb5b50350`; ewebsock 0.8.0, MIT OR Apache-2.0.
- `vendor/ewebsock/LICENSE-APACHE` SHA-256 `8173d5c29b4f956d532781d2b86e4e30f83e6b7878dce18c919451d6ba707c90`.
- `vendor/ewebsock/LICENSE-MIT` SHA-256 `3dedec8a09b1844fab7e18532859d69ab87867ec0409d07349d8900423f2eb17`.
- `vendor/fdk-aac/PROVENANCE.md` SHA-256 `46b12f657ba516a7dfd2811ae5589696ff5a856d48446fae28cc014328bb35e9`; five public headers only, runtime-loaded library, no linker input.
- `vendor/fdk-aac/NOTICE` SHA-256 `95ec80da40b4af12ad4c4f3158c9cfb80f2479f3246e4260cb600827cc8c7836`; Fraunhofer FDK AAC Android licence.

Because RigWeave retains clean-room behavior-only provenance and packages none of those paths, the upstream vendor additions do not alter RigWeave dependencies or distribution contents.

## Commit ledger

| Commit | Date | Upstream summary |
|---|---|---|
| `37b47126ee7fc870d8701d9cb660c576f73ce175` | 2026-08-27T10:33:20Z | Serialise every sign-in, ask before adding a radio at a station, hold a drag's retunes to ten a second, and redial a dropped link by itself (issue #188) |
| `424e569bc97b6b66be6bc29fa8ef131d283676b6` | 2026-08-27T11:56:05Z | Decode xHE-AAC by loading libfdk-aac at run time, and fix the frame borders and output buffer that left it silent (issue #171) |
| `e96f81ac441cd7dd071d488bc2dd57c7d9e24e67` | 2026-08-27T12:20:41Z | Teach the LAN Icom model table the menu numbering of every radio that speaks the protocol, starting with the IC-7610 (issue #190) |
| `2a6f42271d8541c1d32e5f855129fff644a80d4f` | 2026-08-27T12:37:20Z | Mirror the IC-7760's 12 kHz IF so SSB lands on the sideband it was sent on, with an operator override for any radio the model table has wrong (issue #183) |
| `7896580c71281e1197e7679c7a0cb12cfe88d352` | 2026-08-27T13:23:16Z | Reply in the slot the DX is not transmitting in, whether their slot began on a half second or they were last heard several turns ago (issue #191) |
| `f59bb45f6582dd19016d5e6f167e588cf4de0e8e` | 2026-08-27T13:31:12Z | Re-select the FDM-S configuration and start its FIFO only once transfers are queued (issue #178) |
| `803564a61af9bca5914ba2a1a2649b600f8268be` | 2026-08-27T22:31:06Z | Report converter overload, and stop a marginal signal inventing the country code that relabels a station RBDS (issue #173) |
| `ba06b0ac8a19578a407cfcfe895c91e5f6ebb11e` | 2026-08-27T23:52:55Z | Keep an Icom on simplex across a band change, give SSTV its own FM mode for VHF, and hand the SQL rail to the radio that is doing the squelching (issue #192) |
| `ec65192a1df9edae049663d351a35a177e762b85` | 2026-08-28T00:59:18Z | Put the aircraft overhead on a radar display: a 1090 MHz ADS-B decoder, a mode and a panel (issue #160) |
| `8324675fd7edbf080e1e190bc399d0e8b86bf702` | 2026-08-28T13:51:08Z | Bump version |
| `2b5fd6c56290c84b85fd3c428d58b83b9336b49b` | 2026-08-28T14:40:42Z | Slice ADS-B at the phase the burst actually arrived at, and stop throwing away a wide receiver's samples (issue #160) |
| `1f62978036aaa0e3e9f80bca5db4c19102962fd7` | 2026-08-28T14:58:22Z | Let the check sequence pick the slicing alignment, and test against another decoder's off-air recordings (issue #160) |

## Complete changed-path ledger

Every path returned by the immutable GitHub comparison is recorded below.

| Status | Path | Watcher area | Disposition |
|---|---|---|---|
| modified | `.github/workflows/release.yml` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `.gitignore` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `Cargo.lock` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `Cargo.toml` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `README.md` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-adsb/Cargo.toml` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-adsb/examples/adsb_iq.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-adsb/examples/adsb_replay.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-adsb/src/controller.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-adsb/src/cpr.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-adsb/src/crc.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-adsb/src/demod.rs` | LOCAL_DEMODULATION | EXPECTED_REVIEWED |
| added | `crates/sdroxide-adsb/src/frame.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-adsb/src/lib.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-adsb/src/message.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-adsb/src/track.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-adsb/tests/reference_corpus.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-cat/src/civ.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-cat/src/elad.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-cat/src/elecraft.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-cat/src/flrig.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-cat/src/kenwood.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-cat/src/lib.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-cat/src/qrplabs.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-cat/src/rigctld.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-cat/src/yaesu.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-config/src/lib.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-digi/src/controller.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-digi/src/scheduler.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-digi/src/sstv_controller.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-digi/tests/reply_sequencing.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-drm/Cargo.toml` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-drm/build.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-drm/examples/drm_harness.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-drm/src/decoder_tests.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-drm/src/demod.rs` | LOCAL_DEMODULATION | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-drm/src/drm_shim.cpp` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-drm/src/drm_shim.h` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-drm/src/lib.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-dsp/src/adc.rs` | RX_DSP | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-dsp/src/demod.rs` | RX_DSP | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-dsp/src/lib.rs` | RX_DSP | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-dsp/src/modulator.rs` | RX_DSP | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-dsp/src/rds.rs` | RX_DSP | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-elad/src/device.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-elad/src/fpga.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-elad/src/stream.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-elad/src/usb.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-icomnet/src/protocol.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-icomnet/tests/loopback.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-proto/src/lib.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-radio/Cargo.toml` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-radio/src/device.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-radio/src/engine.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-radio/src/source.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-radio/tests/adsb_window.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-radio/tests/rig_squelch.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-radio/tests/sstv_sideband.rs` | SSB_CW | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-rigctld/src/state.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-server/src/lib.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-server/src/session.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-smartsdr/src/net.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-speech/src/text/mod.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-speech/tests/announce.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-speech/tests/normalize.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-tci/src/protocol.rs` | TCI | EXPECTED_REVIEWED |
| added | `crates/sdroxide-types/src/adsb.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-types/src/band_segments.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-types/src/caps.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-types/src/command.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-types/src/controller.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-types/src/drm.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-types/src/lib.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-types/src/meters.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-types/src/mode.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-types/src/radio.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-types/src/rds.rs` | RDS_RBDS | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-types/src/state.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-ui/src/adsb_map.rs` | PROPAGATION | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-ui/src/app/drm.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-ui/src/app/frame.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-ui/src/app/mod.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `crates/sdroxide-ui/src/app/panels/adsb.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-ui/src/app/panels/mod.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-ui/src/app/rds.rs` | RDS_RBDS | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-ui/src/app/settings/mod.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-ui/src/app/settings/radio.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-ui/src/app/top_bar.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-ui/src/help.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-ui/src/input.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-ui/src/lib.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-ui/src/login.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-ui/src/multi.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-ui/src/remote.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `crates/sdroxide-ui/src/widgets/smeter.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `docs/USER_MANUAL.md` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `docs/images/adsb-panel.jpg` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `src/audio_cat_source.rs` | RX_DSP | EXPECTED_REVIEWED |
| modified | `src/elad_source.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `src/icomnet_source.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `src/main.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `src/panadapter_source.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `vendor/dream/PROVENANCE.md` | PLATFORM_OR_OTHER | PROVENANCE_UPDATE_REQUIRED |
| modified | `vendor/dream/src/MSC/xheaacsuperframe.cpp` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `vendor/dream/src/sourcedecoders/AudioSourceDecoder.cpp` | RX_DSP | EXPECTED_REVIEWED |
| modified | `vendor/dream/src/sourcedecoders/fdk_aac_codec.cpp` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `vendor/dream/src/sourcedecoders/fdk_aac_codec.h` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `vendor/dream/src/sourcedecoders/fdk_aac_dll.h` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `vendor/dream/src/sourcedecoders/reverb.cpp` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| modified | `vendor/dream/src/sourcedecoders/reverb.h` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `vendor/ewebsock/Cargo.toml` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `vendor/ewebsock/LICENSE-APACHE` | LICENCE | PROVENANCE_UPDATE_REQUIRED |
| added | `vendor/ewebsock/LICENSE-MIT` | LICENCE | PROVENANCE_UPDATE_REQUIRED |
| added | `vendor/ewebsock/PROVENANCE.md` | PLATFORM_OR_OTHER | PROVENANCE_UPDATE_REQUIRED |
| added | `vendor/ewebsock/README.md` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `vendor/ewebsock/src/lib.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `vendor/ewebsock/src/native_tungstenite.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `vendor/ewebsock/src/native_tungstenite_tokio.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `vendor/ewebsock/src/tungstenite_common.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `vendor/ewebsock/src/web.rs` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `vendor/fdk-aac/NOTICE` | PLATFORM_OR_OTHER | PROVENANCE_UPDATE_REQUIRED |
| added | `vendor/fdk-aac/PROVENANCE.md` | PLATFORM_OR_OTHER | PROVENANCE_UPDATE_REQUIRED |
| added | `vendor/fdk-aac/include/fdk-aac/FDK_audio.h` | RX_DSP | EXPECTED_REVIEWED |
| added | `vendor/fdk-aac/include/fdk-aac/aacdecoder_lib.h` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `vendor/fdk-aac/include/fdk-aac/genericStds.h` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `vendor/fdk-aac/include/fdk-aac/machine_type.h` | PLATFORM_OR_OTHER | EXPECTED_REVIEWED |
| added | `vendor/fdk-aac/include/fdk-aac/syslib_channelMapDescr.h` | PROPAGATION | EXPECTED_REVIEWED |

## Acceptance and package-inclusion proof

- `docs/upstream/SDROXIDE.json` now records the exact v1.5.4 tag, commit, tree, unchanged licence digest, and review time.
- The live watcher returns `NO_CHANGE` at that identity.
- Focused watcher tests prove accepted identity, fail-closed drift, path classification, and zero-exit fixture behavior.
- RigWeave tracked paths contain no `sdroxide-adsb`, `vendor/ewebsock`, or `vendor/fdk-aac` source/package path.
- Existing RigWeave Android implementation provenance remains the earlier v1.5.3 behavior review; this task imports no v1.5.4 behavior.
- The hosted watcher job is mandatory for the final branch; exit 2 can no longer be hidden by job-level `continue-on-error`.
