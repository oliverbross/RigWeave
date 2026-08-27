# Android Remote Client

Open **Remote Stations**. Import a current pairing JSON/QR, submit the signed request, approve the device locally at the station, then Connect. Manual and discovered endpoints cannot connect until a fingerprint-bearing offer has been imported.

Profiles store station id/name, host/port, role, certificate SHA-256, device id, and last-connected metadata. The P-256 private key remains in Android Keystore. Restore never auto-connects. A remote attachment is always labelled `REMOTE · <station>` with connection and lease states across workspaces.

The backend integrates with `RadioPlatformController`; existing Radio, Panadapter, Digi, Keyer/Voice, and Rotator surfaces consume typed snapshots/actions. RX audio and derived spectrum use existing sinks. Global Stop is always reachable. Forget removes profile metadata; station-side revocation must be performed locally.

The debug lab is process-local, opens no external socket/hardware, and always displays `DEMO · NO RADIO`.

