# Native Wavelog integration

## Baselines

- RigWeave start: `c45fb567f2c6db6b986f95cf14d35964511ea26b` (`origin/main`, 2026-08-19).
- Wavelog stable release: `3.1.0`; annotated tag object `465dad8ab4fd33ec637c043466a2829f51ccc87d`; peeled commit `af3256140bd05403b7c4a421746c2ea653a4f04f`.
- Wavelog licence: MIT. RigWeave remains GPL-3.0-only.
- The implementation studies the pinned API and product behaviour. It does not embed PHP, Wavelog pages, its navigation, a web server, or Wavelog source files.

## Placement

RigWeave remains local-first. `QsoDatabase` is the one Android QSO store and existing ADIF import/export remains the serialization boundary. The schema-v8 migration adds only synchronization metadata tables. `WavelogSyncStore`, `WavelogApiV2Client`, and `WavelogSyncEngine` are separate from `MainActivity.kt` and from downstream QRZ/Club Log/eQSL delivery.

One enabled writable Wavelog binding is enforced by a partial unique index. A binding stores a credential alias, never a token. Existing Android Wavelog secrets remain AES-GCM encrypted with an Android Keystore key; existing iOS Wavelog secrets remain in Keychain. A `wl2_` token is never reinterpreted as a legacy API key, and the existing legacy adapter remains available for older installations.

The Apple client now negotiates token metadata/scopes, discovers stations,
performs bounded paginated QSO reads, and drains its existing durable queue with
idempotency keys. A network failure after an Apple write is marked ambiguous for
inspection rather than retried. Android remains the more complete reconciliation
implementation: Apple field-level three-way conflict resolution is still a
documented parity gap.

## Canonical and synchronization rules

- Canonical ADIF keys are uppercase and stably sorted. Callsigns, modes, bands, grids, station callsigns, propagation/satellite values, and confirmation flags are normalized. UTC date/time is normalized without changing the instant.
- Volatile sync state, remote IDs, outbox IDs, last-sync timestamps, and credential aliases are excluded from hashes.
- Unknown valid ADIF fields are retained in `extraAdifFields`, persisted inside the existing QSO details JSON, and exported again.
- Local writes and native Wavelog outbox insertion can share one SQLite transaction. Pending updates coalesce; an unacknowledged create is not discarded.
- Reconciliation uses the last common baseline. Same-field divergent edits create a persisted conflict; disjoint field edits merge; timestamp-based last-write-wins is not used.
- API v2 list pagination uses the server's `page`, `per_page`, `total_pages`, and `has_more` metadata. Initial and full runs checkpoint only after each page transaction commits. Quick sync uses three newest pages as a bounded overlap; it does not claim to find every historic deletion.
- Failed or lost write responses are treated as ambiguous and blocked for reconciliation instead of blindly retried. Validation, authentication, scope, conflict, and destructive failures are never automatically retried.
- API v2 requires HTTPS and platform certificate validation. Bearer tokens and QSO comments are absent from normal diagnostic messages.

## API 3.1.0 contract reviewed

The pinned server exposes `/index.php/api/v2`. API v2 accepts only `wl2_` bearer tokens (with `X-API-Key` as a server fallback), returns `{data, meta}` or `{error}`, and applies resource scopes. RigWeave uses Bearer authorization only.

- `GET token`: owner, expiry, and scopes.
- `GET station`: selectable station ID/UUID/identity; `station:read`.
- `GET qso`: stable page metadata and station scoping; `qso:read`.
- `POST qso`: ADIF import for the mapped station; `qso:write`.
- `PATCH qso/{id}`: partial update only; `qso:write`.
- `DELETE qso/{id}`: explicit destructive action; `qso:delete`.
- `GET statistic`: optional server aggregate, not the authority for local analytics; `statistic:read`.

A read-only token creates a useful local replica. The UI and engine must not imply that local edits can be pushed without `qso:write`.

## Evidence boundary

Automated tests and builds prove source/domain behaviour and migration compatibility only. They do not prove a live token, a particular Wavelog installation, network reliability, remote QSO mutation, physical device behaviour, or radio operation. No credential, remote write, merge, deployment, release, or store submission is part of this branch.

Validated on the programme branch: 213 Android JVM tests, Android multi-ABI
debug assembly, the generic iOS Simulator build, shared-core Debug CTest, the
SGP4 candidate's 168 tests, and a live no-change upstream-watch comparison.
