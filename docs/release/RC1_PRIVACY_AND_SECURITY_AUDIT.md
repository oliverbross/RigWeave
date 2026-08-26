# RC1 Privacy and Security Audit

The audit covers credentials, configuration, QSO/logbook data, provider caches, Groups.io content, support bundles, browser/network trust, raw audio, screenshots, diagnostics and packaged artifacts.

- Credential values are vault-only and excluded from configuration, backup, logs, support bundles and source artifacts.
- Android backup/data-extraction rules exclude protected preferences, databases and runtime transmission/session state.
- Support bundles contain bounded metadata and redacted health state, never message bodies, QSO payloads or credential values.
- SmartLink and browser trust are explicit; no implicit certificate or peer trust is introduced.
- Network and protocol parsers bound responses and retain last-good state on malformed or oversized input.
- PTT/TUNE, TCI TX, N1MM mutation, radio and rotator actions fail closed without connection, capability, readback and explicit authority.
- Raw Digi recording reports drop/quota state and closes on lifecycle teardown.
- Repository scans reject credential-shaped literals and unresolved conflict markers.

Authenticated services, device audio, CAT/PTT/TUNE, RF, rotator movement, signing and distribution remain separate live gates.
