# RigWeave product contract

## Positioning

> RigWeave is a radio-native portable operating cockpit that connects discovery, tuning, operating, logging, synchronisation, and progress without requiring fabricated state or permanent network access.

RigWeave is local-first. Radio control and local logging must remain useful without a cloud service. Online enrichment may improve the operating loop but must degrade independently and honestly.

## Current product

- Native Apple mobile client using SwiftUI. The current target and physical evidence are iPad-focused; iPhone support is not claimed.
- Native Android client using Jetpack Compose.
- Shared C++17 core exposed through a C ABI.
- Deep Elecraft KX3/KX2 integration: observed CAT state, radio controls, transport adapters, safety classification, logging, real spectrum paths, and DX intelligence.
- Local SQLite journals and ADIF workflows.
- Optional Wavelog, QRZ/HamQTH, CTY, cluster, NOAA/solar, and other Android Neural DX data sources where implemented and configured.
- CW text macros with explicit operator safety controls; voice macros are not implemented.

The clients do not have identical surface coverage. Android currently contains the larger Neural DX workspace; Apple contains the physically proven iPad KXUSB and stereo-I/Q path. Cross-platform claims must name the client and evidence level.

## Approved direction

1. KX3/KX2 Studio: harden the existing panadapter/audio-source path, then RX/TX EQ and profiles, then voice macros.
2. Portable Chase for POTA, SOTA, and WWFF.
3. Portable Activate.
4. Sync and Progress.
5. FlexRadio SmartLink through legitimate official interfaces and authentication.
6. One Qt 6/QML/CMake desktop client for macOS, Windows, and Linux.
7. QMX, rigctld, additional radios, and additional programmes only after hardware/data/licence gates.

See [docs/ROADMAP.md](docs/ROADMAP.md). Roadmap items are not current capabilities.

## Product principles

1. **Observed radio truth.** No simulated radio, fabricated spectrum, fixture spot, demonstration QSO, invented service success, or hidden fallback in production paths.
2. **Local durability before network convenience.** Local logging remains authoritative until a configured authority accepts a durable outbox item.
3. **Explicit transmit safety.** Transmit-capable actions are operator-initiated, bounded, abortable, and never blindly retried.
4. **Authority-aware synchronisation.** Wavelog mode and local-log mode remain distinct; direct connectors must not duplicate uploads when Wavelog is authoritative.
5. **Graceful per-service degradation.** One unavailable provider must not falsify or disable unrelated local work.
6. **Platform-native interaction.** SwiftUI and Compose remain native; future desktop UI is Qt/QML.
7. **Lean shared core.** Put portable protocol, DSP, parsing, ranking, logging-domain, provider-neutral, and retry-policy logic in C++ when that is genuinely useful. Keep hardware, secure storage, document picking, lifecycle, networking orchestration, and UI platform-specific.
8. **Preserve the working KX3/KX2 path.** Do not replace proven code with a theoretical abstraction.
9. **Evidence before support claims.** Source, tests, builds, simulator/emulator, physical device, real radio/audio, and authenticated service are distinct evidence levels.

## Licence and reuse

RigWeave is GPL-3.0-only. Distributed covered binaries require complete corresponding source and retained notices; charging for distribution or services remains permitted.

Future third-party reuse must record the source URL, immutable upstream commit, original path, licence, copyright/provenance, RigWeave modifications, applicable notice entries, dependencies, and corresponding-source obligations.

Nexus is an evaluated external upstream, not an incorporated dependency. Its name or licence does not imply endorsement or make every vendored/dependency component automatically reusable. Component-specific authorisation and review are required before reuse.

Public distribution through Apple-controlled channels remains an unresolved legal/platform risk. No App Store submission or compatibility claim is part of this contract.
