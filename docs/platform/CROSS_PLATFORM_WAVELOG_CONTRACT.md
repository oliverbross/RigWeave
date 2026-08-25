# Cross-platform Wavelog contract

Android retains its single `QsoMutationCoordinator` and outbox. Windows retains its independent local database, link, checkpoint, conflict and outbox tables. Implementation language and database bytes are deliberately not shared.

Both clients follow the same API-v2 semantics for station discovery, initial/quick/full synchronization, create, whitelisted update, delete, ambiguous create/delete, conflict, tombstone, checkpoint resume, bounded retry and station isolation. `configuration_wavelog_golden.json` names the required scenarios without containing credentials.

Ambiguous writes block automatic replay until reconciliation. Conflicts are explicit. Unknown ADIF remains preserved. Local acceptance records persistence separately from remote delivery. Authenticated live Wavelog evidence remains pending and no production local server is introduced.
