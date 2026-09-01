# Web Agent domain journal M4

The M4 domain journal is a separate, bounded outage-handoff channel. It is not the observer lifecycle journal and it is not a second logbook. Ordinary local logging writes directly to the Application Service and does not use this journal.

Each entry is an opaque `APPLICATION_SERVICE_AEAD_V1` ciphertext envelope with a UUID event ID, native station identity, Application Service identity, `APPLICATION_SERVICE_OUTAGE` origin, UTC creation and expiry, `rigweave.qso-event` schema version 1, ciphertext SHA-256, and an Agent-owned acknowledgment state. The Agent verifies canonical base64, the ciphertext hash, metadata bounds, a maximum 16 KiB ciphertext, and a maximum seven-day lifetime. It retains at most 256 entries. Acknowledgment is idempotent and retains the envelope for 24 hours before pruning so a lost response cannot turn into destructive ambiguity.

Only the user-scoped stationd administration socket exposes the pending/acknowledged envelopes. It performs no QSO queries, conflict resolution, projection, provider delivery, radio command, or UI work. The ciphertext key remains an Application Service concern; the Agent stores no new secret and cannot interpret the QSO payload.

The `domain-journal-append` and `domain-journal-ack` administration actions are intended for the local Application Service. `--domain-journal` is a bounded local diagnostic listing. Configuration persistence stores only opaque ciphertext and public metadata; private keys and provider credentials are excluded.

