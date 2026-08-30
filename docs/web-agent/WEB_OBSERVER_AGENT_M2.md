# Web Observer Agent M2

`rigweave-stationd` is the canonical native owner for station identity, observer authority, live state, and media. M2 adds bounded Hub identity/sign administration, explicit observer approval/revocation, a sanitized journal, and deterministic `--debug-no-radio` state/media.

The debug mode binds loopback, disables rigctld, TCI, raw IQ, remote TX and rotator policy, uses an ephemeral configuration root and session vault, and emits `DEMO · NO RADIO`. Production mode retains the system credential vault. Browser/Hub code never receives a private key.

This branch does not authorize CAT, PTT, TUNE, TX audio, QSO mutation, rotator movement, hardware access, or RF.
