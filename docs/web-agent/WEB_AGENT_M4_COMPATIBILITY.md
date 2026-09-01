# Web Agent M4 compatibility

- Native base: `aae92b04ee14333e6765c952759bf2d7e18b636f`.
- Agent protocol: `1.0`, unchanged.
- observer/media protocols: `1.0`, unchanged.
- domain journal envelope: `rigweave.agent-domain-envelope/1` carrying opaque `rigweave.qso-event/1` ciphertext.
- protection label: `APPLICATION_SERVICE_AEAD_V1`.
- maximum ciphertext: 16 KiB; maximum rows: 256; maximum unacknowledged lifetime: seven days; acknowledged retention: 24 hours.
- frozen Android parity SHA: `1e917fd2a0ec38a6b66eb9ab211d30ab48c331d3`, unchanged and not modified by this branch.

Compatibility is additive. An M1–M3 Hub can continue using the unchanged observer contract and ignore the local administration actions. An M4 Application Service must validate identity, mapping, schema, hash, expiry, idempotency, and authority before accepting a journal event. Raw SQLite synchronization is not supported.

Focused proof is `remote_station_service_tests`: valid storage, duplicate-id idempotency, changed-hash refusal, bounded ciphertext refusal, idempotent acknowledgment, delayed retention, and absence of decoded QSO fields.
