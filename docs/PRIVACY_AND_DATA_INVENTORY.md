# Privacy and Data Inventory

## Sweep 2 private caches and settings

SCP.DB, IOTA JSON and user-selected lawful portable CSV files are stored only in app-private storage with source metadata and SHA-256. None is bundled or uploaded. Contest schema 2 stores temporary event QSOs until explicit merge. Groups.io auto-download runs only while its foreground screen is active; preview excerpts are transient Compose state and no message body enters diagnostics.

## Sweep 1 Portable and browser data

Portable SOTA may open a receive-only TCP session to cluster.sota.org.uk:7300 while the Portable workspace is foregrounded. The login value is read from the existing user-configured DX-cluster username; it is not hard-coded, copied into reports, or used to post spots. Parsed rows are bounded, expire after one hour, and are enriched from the existing app-private SOTA catalogue cache for map coordinates. POTA/SOTA in-app pages enable JavaScript and DOM storage only on exact reviewed HTTPS hosts; unreviewed redirects lose that privilege and require external-browser confirmation. Third-party cookies, file/content access, and mixed content remain disabled.

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
