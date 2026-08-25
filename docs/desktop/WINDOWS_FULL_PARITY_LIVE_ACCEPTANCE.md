# Windows Full Parity Live Acceptance

## Software evidence complete

- Qt 6.11.2 Debug build and 6/6 CTest/QML targets.
- Provider response matrix, schema gates, authority/safety contracts and deterministic scale probe.
- Three gallery profiles with 25 distinct frames each and no runtime warning.
- Unsigned macOS application package and launch smoke.
- Rust, normal native, ASan/UBSan, Android and unsigned iOS regression gates.

## Mandatory live items pending

| Layer | Required acceptance | Current state |
|---|---|---|
| Windows package | install/uninstall, Start menu, per-user paths, clean machine launch | Hosted artifact and physical Windows review pending |
| Credential vault | create/read/delete alias without exposing secret | Windows live proof pending |
| Wavelog | authenticated pull/push/conflict behavior | Pending; no credentials used |
| DX cluster | login, commands, reconnect, parse, shared spot propagation | Pending |
| Radio | capability/readback, connect/disconnect, CAT controls | Pending per profile |
| TX controls | dummy-load PTT/TUNE/direct-tone with explicit approval | Not authorised; pending |
| Panadapter/audio | real I/Q/audio routing, device changes, shutdown | Pending |
| Rotator | protocol capability, Stop and movement | Movement not authorised; pending |
| Providers | live success/empty/error/cache/redirect/cooldown | Fake policy passed; authenticated/live pending |
| Notifications/browser/files | Windows-native behavior | Pending |

No live result is inherited from Android, macOS, an earlier Alpha SHA, or deterministic fixtures.
