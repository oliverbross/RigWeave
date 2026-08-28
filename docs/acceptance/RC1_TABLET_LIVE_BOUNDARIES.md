# RC1 Tablet Live Boundaries

## Accepted evidence classes

- Source/build evidence proves only that the cited code and package were built and tested.
- Device/process evidence proves only installation identity, data preservation, launch, lifecycle, resource, and crash behavior.
- Unlocked visual evidence proves only what is visible in the timestamped screenshot and matching UI hierarchy.
- Read-only service evidence may establish successful reads from restored providers; it does not establish authenticated mutation.
- Deterministic Debug Lab evidence is labelled `DEMO · NO RADIO` and cannot establish live network, physical radio, RF, audio, TX, or movement acceptance.

## Prohibited actions in this sweep

Do not connect a live radio or rotator, open a live TCI hardware session, send CAT commands, key PTT, invoke TUNE, transmit RF, move an antenna, alter remote-station authority, post or modify Groups.io content, mutate Wavelog records, expose credentials, uninstall the package, clear app data, or downgrade the application.

`GLOBAL STOP` may be inspected and exercised only in a state where no live hardware or authority exists. A successful process-safe stop is not a physical emergency-stop acceptance claim.

## Pending physical acceptance

Audible RX/TX audio, stereo I/Q fidelity, CAT control/readback, PTT/TUNE, RF output, remote writer/TX leases, rotator movement, signed remote compatibility, and authenticated service mutations remain pending until their separately authorized acceptance programmes provide direct evidence. Release wording must keep those states distinct from this tablet sweep.

## Protected tablet invariants

The package is `app.rigweave.mobile`. Installation is permitted only when the existing package is present, a private-data backup and hashes exist, the candidate signer matches, versionCode is not lower, and the candidate contains arm64. Use only `adb install -r`, then verify the same UID, first-install time, signer, schemas, QSO/projection counts, protected preference hashes, installed APK hash, safe disconnected/disarmed launch, and an empty fresh crash buffer.
