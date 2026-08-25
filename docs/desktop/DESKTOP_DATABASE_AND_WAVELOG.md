# Desktop database and Wavelog contract

## Canonical QSO store

The desktop database is independent from Android private storage but preserves schema-16 field semantics. `qso` stores canonical fields, contest/satellite/portable values, confirmation states, local/remote provenance, unknown ADIF JSON, remote identity and delete truth. `qso_projection` is derived and indexed for callsign, entity, band, mode, grid, zones, WPX prefix, portable reference, confirmation, station scope and keyset paging. `qso_tombstone` preserves deletion intent.

Interactive queries cap pages at 250 and use `(created_at,id)` cursors. The deterministic scale gate inserts 100,000 disposable rows and measures a 250-row query without full materialisation. Projection verification compares canonical/projection cardinality; rebuild is transactional.

ADIF is read/written in bounded chunks. Field lengths are bounded, cancellation is checked between chunks/records, a temporary `QSaveFile` provides atomic export, and unknown fields round-trip unless they collide with a canonical field.

## Wavelog API-v2

The normalized endpoint is HTTPS and ends in `/index.php/api/v2` or `/api/v2`. The bearer token must start `wl2_` and is resolved from a vault alias. Network response bodies are capped at 4 MiB and errors are sanitized.

The local sync tables implement one active binding, coalesced outbox, local/remote links with canonical baselines, restart checkpoints, conflicts and resolution intent. Initial/Quick/Full scans use pages of 250. Three-way merge distinguishes converged, push-local, pull-remote, safe-merge and conflict outcomes. Create/delete transport failures with unknown or 5xx outcomes become blocked ambiguous writes; they are never blindly retried. Keep Local, Keep Remote and Merge are explicit operator decisions. Tombstones and stable full-inventory truth govern remote deletion; cancellation never infers deletion.

The test endpoint is deterministic and needs no credential. It proves store/restart and merge behavior, not authenticated Wavelog success.
