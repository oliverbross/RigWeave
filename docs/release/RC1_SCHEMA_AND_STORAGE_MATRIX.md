# RC1 Schema and Storage Matrix

| Domain | Canonical version | Platforms | Required behavior |
| --- | ---: | --- | --- |
| QSO | 16 | Android, Apple, desktop semantic equivalent | fresh create; all accepted upgrades; reopen; future rejection; corrupt failure; no destructive fallback |
| Neural | 5 | Android, desktop semantic equivalent | scoped evidence retention, 180-day compaction, outage truth, interruption resume |
| Contest | 2 | Android, Apple/desktop semantic equivalent | staged mutations, session recovery, database isolation |
| Digi | 2 | Android, desktop semantic equivalent | bounded payloads, session recovery, raw-recorder quota/drop truth |
| Groups.io | 2 | Android, Apple, desktop semantic equivalent | FTS/archive recovery, draft isolation, quota pause/resume |
| DX Chaser | 1 | Android, Apple/desktop semantic equivalent | session isolation and safe resume |

The migration suite covers fresh creation, accepted historical upgrades, reopen, future-schema rejection, corrupt-input failure, non-destructive failure, retention/compaction, interruption resume and isolation. Desktop `PRAGMA user_version=16` and domain stores are semantic counterparts, not independent authorities. SQLite is never deleted as a recovery strategy.

The scale profile exercises 100,000 QSO rows, 180 days of Neural evidence, 30,000 Groups.io messages, keyset queries, streaming export, provider lifecycle, compaction and recovery in disposable databases.
