# M9 compatibility

- Sync protocol `rigweave.sync.v1` major 1/minor 0.
- Android schema 18; Apple sync schema 1; Local Hub schema 7; Hosted schema 2.
- Maximum batch 200 events/4 MiB.
- Required domains: QSO, tombstone/restore, confirmation, conflict resolution, station/logbook mapping, goal, watchlist.
- Unsupported major, future schema, forbidden domain, stale key/sequence, invalid signature, or oversized input fails closed.
