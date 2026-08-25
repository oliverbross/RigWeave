# Schema 16 cross-platform contract

Android remains the authority for its QSO schema version 16 and canonical mutation ownership. Windows uses an independent schema-16 SQLite database and projection. The files are not claimed to be byte-compatible or directly interchangeable.

Semantic interoperability is defined by `fixtures/platform/schema16_qso_golden.json`: stable QSO identity, station scope, canonical ADIF fields, contest/satellite/portable projections, revision and tombstone intent, and preservation of unknown ADIF fields. Each client owns its migrations, transactions, WAL/lock behavior, projections and recovery.

Indexes are implementation-local but must support bounded identity lookup, station isolation, revision/tombstone reconciliation and keyset paging. Downgrade is fail-closed; neither client may open a newer production schema by pretending it is older.

Proof means that both clients parse and preserve the fixture semantics. It does not mean their SQLite pages, journal modes, generated indexes or database files match byte-for-byte.
