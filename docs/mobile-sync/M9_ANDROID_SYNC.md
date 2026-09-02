# M9 Android sync

Schema 18 adds spaces, devices, key metadata, outbox/inbox/results, cursors/checkpoints, conflicts, peer state, audit, domains, and links. Local linked mutations enqueue atomically. `AndroidMobileSyncEngine` uses Keystore P-256 signed HTTPS, bounded push/pull, canonical remote apply, and unique battery/network-constrained WorkManager plus manual Sync Now. It never owns radio/CAT/PTT/TUNE/TX or clears app data.
