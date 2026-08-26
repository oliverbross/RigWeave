# Desktop Domain Schema Matrix

Desktop databases are app-private, independently migrated and never import Android private data or credentials.

| Store | Schema | Primary owned records | Safe restore/migration invariant |
|---|---:|---|---|
| Canonical QSO | 16 | QSOs, revisions, outbox/projections | Transactional migration; canonical mutation owner only |
| Neural DX | 5 | Evidence, calibration, opportunities | Station-scoped retention; empirical labels preserved |
| Digi | 2 | Sessions, decodes, SSTV metadata | Sessions restore stopped; no TX authority |
| Contest/SCP/N1MM | 2 | Sessions, staged QSOs, score, SCP manifest/callsigns, peer lifecycle and deduplicated packet ledger | Atomic SCP last-good promotion; N1MM inactive, loopback, untrusted and unarmed restore |
| Groups.io | 2 | Memberships, groups, topics, messages, drafts, outbox, delivery ledger, FTS | Alias only; no token; no automatic send/refresh |
| Engagement | 1 | Chaser attempts and local engagement state | No target lock or transmit authority restored |
| Portable/Operations/Satellite caches | 1 | Last-good catalogue/calendar/TLE projections | Explicit foreground refresh and bounded retention |

Each migration runs in a transaction, rejects a newer unknown schema and is covered by fresh/current/upgrade/reopen/corrupt-input tests. Backup/rollback evidence is recorded by the migration suite; no downgrade is attempted.
