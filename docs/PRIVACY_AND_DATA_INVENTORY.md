# Privacy and Data Inventory

| Data | Storage owner | Export / support-bundle rule |
|---|---|---|
| QSO records and projections | SQLite, schema 13/2 | Never in configuration or support ZIP |
| Neural observations/cache | SQLite, schema 5 | Counts/bytes only |
| Digi sessions | SQLite, schema 2 | Counts/status only; no decoded content |
| Groups.io messages/drafts | SQLite, schema 2 | Counts/status only; no bodies or attachments |
| Wavelog token / Groups.io API key / callbook passwords | Keystore/Keychain/private credential stores | Never exported |
| Station profile and safe preferences | Shared preferences | Configuration bundle only, previewed before restore |
| Radio/TX runtime state | Volatile/controller state | Never exported; restore always disarms |
| Diagnostics | Generated support ZIP | Build, schemas, byte counts, statuses and upstream pins only |

Configuration hashes detect accidental or malicious mutation but are not signatures of trust. Import remains a deliberate local operator action.

## Network and lifecycle

- Wavelog uses the operator-configured HTTPS root; its token remains in the platform credential store.
- Groups.io uses its documented HTTPS API only after enablement/authentication; Home reads the local database and does not refresh on recomposition.
- DX cluster/RBN, PSK Reporter, DX News, solar/weather and satellite catalogue/status providers retain their existing bounded controllers, endpoint attribution, response limits, last-good rules and close/cancellation lifecycle.
- Background/foreground and connectivity changes flow through the owning controller. A malformed or oversized success response cannot replace last-good data.
- External provider/source links pass through the existing secure URL/browser policy; links do not confer transmit or authentication authority.

## Operator controls

Local log ADIF export/import, configuration export/import and the sanitized support ZIP require deliberate operator actions. Existing cache-clear actions remove only re-fetchable provider/archive data; QSO records, drafts/outboxes and credential stores are not cleared by those controls. Data deletion remains in each owning workspace and is never triggered by navigation, a provider response or configuration restore.
