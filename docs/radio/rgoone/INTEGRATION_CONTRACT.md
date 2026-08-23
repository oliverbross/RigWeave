# RGO ONE Integration Contract

## Ownership

This package is not a second radio authority. The later central adapter owns selection
of the existing Android USB serial transport, global radio state, TX safety, audio,
presets, recovery, and the Radio screen.

The package must remain free of imports or calls to `UsbRadioTransport`, `RadioBackend`,
`NativeCore`, `MainActivity`, QSO/log clients, Digi, Panadapter, or audio owners.

## Ports

- `RgoOneSerialPort`: stable identity, bounded open/write/exchange/close. The adapter
  must return at most the requested byte count and must not retry action commands.
- `RgoOneUsbIdentityPort`: optional exact generation and USB-audio descriptor evidence.
- `RgoOneSafetyPort`: one decision for every non-read action.
- `RgoOneActionPort`: typed UI action emission only.
- `RgoOneClock`: deterministic timestamps for stale truth and tests.
- `RgoOneTransportPort`: common transport contract; no Android USB dependency.

Only one `RgoOneConnectionController` may own a selected serial port. The adapter must
not run a second polling loop or share the port with another CAT controller.

## Connection handoff

1. Resolve a stable central device identity.
2. Supply operator generation or exact `RgoOneUsbIdentityPort` evidence.
3. For USB CAT, open without applying menu-22 baud/framing assumptions.
4. For TTL, require an official/observed framing profile and one of the documented baud rates.
5. Let the controller prove confirmed V6 with `ID006` and read firmware.
6. Subscribe to snapshots and map them into the existing central `RadioState` later.
7. On disconnect, cancel polls, close exactly once, and publish stale/disconnected truth.

## Action handoff

Compose emits `RgoOneAction`. The integration adapter must preserve its action class:

- `READ_ONLY`: may be routed while connected and confirmed;
- `SAFE_SET`: requires central context validation and package write confirmation;
- `EDGE_TRIGGERED`: one attempt only;
- `TRANSMIT` / `TUNE`: central reviewed TX path, abort path, and operator initiation;
- `MEMORY_WRITE`: explicit memory review plus package memory-write enablement.

Preset recall may use receive VFO/mode setters. It must never silently invoke `MW`.

## Audio

An audio profile exists only if USB descriptors prove rate, channels, and direction.
The adapter must request the existing audio owner. The profile explicitly reports
`isIqSource=false`; it cannot enable Panadapter or be labelled I/Q without later physical
and documentary proof.

## Privacy and recovery

The central adapter must not request SN for routine operation. If identity diagnostics
require it, only the returned SHA-256 may cross the package boundary. Safe settings may
be added to configuration recovery later, but import must keep PTT, tune, pending commands,
TX arm, write confirmation, and memory write disabled.
