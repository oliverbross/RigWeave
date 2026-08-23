# QMX/QMX+ semantic integration contract

The later integration adapter binds these package ports without moving ownership into the QMX core.

| Port | Later owner | Contract |
|---|---|---|
| `QmxSerialPort` | central radio/USB CAT owner | One exact primary CDC transaction stream; sanitized failures. |
| `QmxUsbIdentityPort` | Android USB discovery | Stable hashed identity and current descriptors; never expose raw serials. |
| `QmxUacAudioPort` | central audio ownership | Same-device route proof, physical format and orientation; no microphone fallback. |
| `QmxRadioActionPort` | central Radio safety/router | Review/confirm/execute typed actions; surface never sends CAT. |
| `QmxDigiToneTxPort` | existing Digi + radio owner | Immutable-plan authorization and exclusive CAT-tone execution channel. |
| `QmxPanadapterPort` | central Panadapter | Apply the QMX IF/channel/correction/settings contract. |
| `QmxSafetyPort` | central operating-context/TX safety | Context/device generation, abort state and policy-owned cleanup retry. |
| `QmxClock` | platform monotonic/wall clock adapter | Wall time for slot identity only; monotonic time for cadence. |

## Required adapter sequence

1. Discover the exact USB composite device and hash any private serial before constructing `QmxUsbIdentityEvidence`.
2. Bind interface 0 as primary CAT only when its descriptors match the selected device; keep extra CDC closed.
3. Prove the same device's 48 kHz stereo UAC route and I/Q orientation.
4. Attach the connection controller and wait for frequency/mode, Q9 ON, Q3 OFF and exact UAC readiness.
5. Publish the immutable snapshot to the central Radio/System Health adapters.
6. Give the central Panadapter the QMX adapter contract; central Panadapter retains screen, waterfall and Band Map ownership.
7. Route surface actions through central confirmations. Do not call `submit` for TX/tune confirmation actions.
8. Expose direct tone TX only after a non-transmitting integration proof establishes the capability; never probe TX automatically.
9. On any route/radio/context generation change, abort the tone backend, return to RX through safety policy, call `routeLost`, and require a fresh exact handshake.

## Preset and health rules

Only `QmxSettingsDocument.toSafeMap()` may enter existing preset/configuration storage. Runtime TX, arms, tune, terminal and command queue state are absent by construction. Health may consume `QmxDiagnostics`; terminal content remains excluded by default.
