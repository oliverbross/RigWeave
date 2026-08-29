# Changelog

## Android 1.0 final candidate (unreleased)

- Preserves every accepted RC1 tablet-sweep correction inside the Android 1.0 hardening architecture.
- Retains grouped Hamlib selection, real Rotator configuration, consolidated feature Settings, adaptive navigation, database/index optimization, lifecycle fixes, R8 shrinking, and accessibility repairs.
- Fixes Linux Secret Service build dependencies and makes the reviewed SDRoxide watcher gate mandatory.
- Adds a privacy-preserving, reproducible 67,223-row SQLite projection benchmark for Android performance regression evidence.
- Adds explicit cross-platform product metadata while keeping the RigWeave suite on the existing RC release line.
- Does not move `v0.1.0-rc.1`, publish `v1.0.0`, deploy to a store, or claim CAT/PTT/TUNE/RF/audio/rotator acceptance.

## 0.1.0-rc.1

- Consolidated accepted Android, iPhone/iPad, macOS, Windows, Linux, and station-service lineages.
- Added adaptive Opus Remote Station RX, PCM16 fallback, and optional bounded raw I/Q.
- Added pinned native Remote Station clients for Android, SwiftUI, and Qt/QML.
- Added Android MIDI/Bluetooth MIDI/USB MIDI and HID control-surface mapping with immutable Global Stop.
- Added full Linux GUI, libsecret credential storage, TGZ/DEB packaging, and native arm64 stationd CI.
- Preserved fail-closed PTT/TUNE/RF/movement and unsigned release boundaries.
