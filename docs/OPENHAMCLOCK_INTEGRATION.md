# OpenHamClock native integration record

## Provenance

- Source: https://github.com/accius/openhamclock
- Inspected commit: `d4a50eaaa61d3432a1de5f80cbe61790739930a5`
- Inspected release metadata: `26.5.0`
- Licence: MIT
- Copyright notice: Copyright 2024-2026 OpenHamClock Contributors
- Upstream paths inspected: `README.md`, `LICENSE`, `package.json`, `src/`, and `server/`

## RigWeave implementation

RigWeave does not vendor or execute the upstream React/Vite client, Express
server, deployment files, plugins, artwork, or JavaScript dependencies. The
Android implementation is a new native Jetpack Compose surface in
`HamClockHomeScreen.kt`, integrated through `MainActivity.kt`. It adopts the
operating-dashboard concept and module hierarchy while retaining RigWeave's
Flightline design system, platform navigation, accessibility, touch targets,
local-first state, and explicit provider failure states.

The Home surface reuses these existing RigWeave authorities:

- `AppController` and the selected Wavelog station for callsign and grid;
- `RadioState` for observed CAT and transceiver state;
- `FeatureController` for DX cluster spots and solar indices;
- `NeuralDxController` for weather, PSK Reporter, propagation, WSPR, and satellites;
- `PortableController` for authorised POTA and WWFF feeds and the explicitly
  unavailable SOTA live-feed state;
- `CtyController` for approximate reporting-path endpoint resolution.

No OpenHamClock-specific callsign, location, cluster, provider credential, or
radio setting is created. Existing RigWeave persistence therefore remains the
single settings authority across reloads, replacement development builds, and
the Home, Radio, DX, Portable, Log, and Settings surfaces.

## Deliberately excluded upstream infrastructure

The port excludes upstream deployment/update/donation UI, Node proxies,
standalone profiles, plugins, cloud hosting controls, and bridge services that
would duplicate RigWeave or have no implemented local authority (including
APRS, Meshtastic, rotator, and upstream WSJT-X relay operation). Their absence
is intentional: RigWeave does not present an integration until it has a real,
authorised data source and an honest unavailable/error state.

This record and the root `NOTICE` retain the upstream licence and attribution.
RigWeave remains distributed under GPL-3.0-only as described in `COPYING`.
