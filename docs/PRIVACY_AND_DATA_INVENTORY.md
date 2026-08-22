# Privacy and Data Inventory

| Data | Storage owner | Export / support-bundle rule |
|---|---|---|
| QSO records and projections | SQLite, schema 13/2 | Never in configuration or support ZIP |
| Neural observations/cache | SQLite, schema 5 | Counts/bytes only |
| Digi sessions | SQLite, schema 2 | Counts/status only; no decoded content |
| Groups.io messages/drafts | SQLite, schema 2 | Counts/status only; no bodies or attachments |
| Contest sessions/serial links/derived score | `rigweave-contest.sqlite`, schema 1 | Operational DB excluded from backup/support; safe defaults only in configuration bundle |
| N1MM LAN metadata/claims | Contest store plus volatile network controller | Counts/sanitized status only; no raw XML, IP, QSO or exchange payload |
| Keyer profiles/hotkeys/voice references | Keyer preferences and private voice store | Stable definitions exportable; no audio bytes/path, queue, resolved QSO text or arm |
| DX Chaser sessions/attempts/rarity | `rigweave-dxchaser.sqlite`, schema 1 | Operational DB excluded from backup/support; no QSO truth/provider body/decode transcript |
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
- Background stops Keyer/repeat CQ, pauses Contest, closes N1MM and stops Chaser; none of those active states restore after process recreation or configuration import.

## Operator controls

Local log ADIF export/import, configuration export/import and the sanitized support ZIP require deliberate operator actions. Existing cache-clear actions remove only re-fetchable provider/archive data; QSO records, drafts/outboxes and credential stores are not cleared by those controls. Data deletion remains in each owning workspace and is never triggered by navigation, a provider response or configuration restore.
