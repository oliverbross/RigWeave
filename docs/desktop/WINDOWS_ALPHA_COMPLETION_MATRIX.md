# Windows Alpha completion matrix

The status describes implemented source behavior. Hosted, package and live-evidence columns remain separate so a source-complete row cannot be mistaken for physical or authenticated acceptance.

| Capability | Status | Deterministic evidence | Live evidence |
|---|---|---|---|
| Qt/QML Flightline shell, menu, shortcuts, resizable navigation | `WINDOWS_ALPHA_COMPLETE` | QML test and launch smoke | visual review pending |
| Home and System Health | `WINDOWS_ALPHA_COMPLETE` | source/service graph tests | provider review pending |
| Schema-16 local database and projection | `WINDOWS_ALPHA_COMPLETE` | migration, projection, 100k keyset test | user-data import pending |
| Logbook/Fast Entry/ADIF | `WINDOWS_ALPHA_COMPLETE` | unknown-field round trip and paging | operator workflow pending |
| Wavelog API-v2 | `WINDOWS_ALPHA_COMPLETE` | deterministic endpoint, merge/outbox tests | authenticated service pending |
| DX Cluster and shared spot repository | `WINDOWS_ALPHA_COMPLETE` | parser/dedup fixture | live cluster pending |
| Intelligent Band Maps | `WINDOWS_ALPHA_COMPLETE` | one-repository model, QML/source checks | Windows visual acceptance pending |
| Local Intelligence | `READ_ONLY_COMPLETE` | projection query tests | map provider intentionally absent |
| Hamlib generic radio | `WINDOWS_ALPHA_COMPLETE` | pinned build, registry/dummy gates in CI | physical radio and TX absent |
| Hamlib rotator | `WINDOWS_ALPHA_COMPLETE` | safe restore/dummy gates | physical movement absent |
| Receive-only Panadapter | `WINDOWS_ALPHA_COMPLETE` | deterministic stereo tone; missing-route refusal | Windows stereo route pending |
| Configuration and recovery | `WINDOWS_ALPHA_COMPLETE` | whitelist/rollback/privacy tests | operator import pending |
| Windows Credential Manager | `WINDOWS_ALPHA_COMPLETE` | fake contract; Windows compile | live credential write pending |
| About/licences/support ZIP | `WINDOWS_ALPHA_COMPLETE` | privacy test and packaged notices | manual contents review pending |
| Digi | `FOUNDATION_COMPLETE` | truthful inert page | local modem/TX deferred |
| Contest | `FOUNDATION_COMPLETE` | truthful inert page | scoring deferred |
| Groups.io | `FOUNDATION_COMPLETE` | vault/offline contract | authenticated access deferred |
| Portable/Operations | `FOUNDATION_COMPLETE` | provider registry page | provider/map acceptance deferred |
| Satellite/QO-100 | `FOUNDATION_COMPLETE` | SGP4 linked | pass UI/live TLE deferred |
| Windows ZIP/installer | `WINDOWS_ALPHA_COMPLETE` | exact-SHA workflow creates, measures and hashes | installation review pending |
| macOS desktop proof | `FOUNDATION_COMPLETE` | same-source build/test/unsigned app workflow | signing/notarization deferred |

No row claims Android visual parity, physical transmit, rotator movement, authenticated Wavelog, live cluster/audio, official award credit, signing, store readiness, or macOS product completion.

## Superseded by full-parity audit

The Alpha rows remain historical evidence for their exact SHA. The successor audit is [`WINDOWS_FULL_PARITY_MATRIX.md`](WINDOWS_FULL_PARITY_MATRIX.md): 14 of 31 rows are `SOURCE_COMPLETE`, while 17 are `FOUNDATION_WIRED`. The successor therefore retains a PARTIAL verdict and does not inherit Alpha hosted or package evidence.
