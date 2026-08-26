# Desktop Owner Graph v1

The closure keeps one authority per concern. QML is a view/action client and creates no database, transport, device, worker, audio, radio, rotator, or provider owner.

| Concern | Sole desktop authority | Consumers |
|---|---|---|
| Composition root, operating context, workspace actions, Global Stop | `DesktopApplication` | all workspaces and native menus |
| Canonical QSO, mutation, projection | `QsoDatabase` through `DesktopApplication` | Logbook, Contest merge, Digi completion, analytics |
| Wavelog outbox/sync | `WavelogSyncEngine` | Sync, QSO mutation results |
| Cluster transport | `ClusterClient` | shared `SpotRepository` |
| Shared spots and Band Maps | `SpotRepository` plus the closure evaluator in `DesktopParityPlatform` | Band Maps, DX, Home, Chaser |
| Provider/cache platform | `DesktopParityPlatform` | Home, Neural, DX, Portable, Operations, Satellite |
| Neural evidence/outlook | `DesktopParityPlatform` schema-5 domain owner | Home, DX, Band Maps, Intelligence |
| Radio connection and native profiles | `DesktopRadioController` | Radio, Panadapter, Digi/Keyer capability gates, presets/EQ |
| Audio route and Panadapter | `DesktopPanadapterController` | Panadapter and Digi receive handoff |
| Digi and DX Chaser | `DesktopParityPlatform` backed by the linked native modem and schema 2/1 stores | Digi workspace, Contest/Chaser handoffs |
| Keyer | `DesktopParityPlatform` keyer runtime | Radio, Digi, Contest |
| Contest and N1MM | `DesktopParityPlatform` schema-2 staging/runtime | Contest, Band Maps, canonical QSO merge |
| Portable, Operations, Satellite | `DesktopParityPlatform` provider/cache projections and shared SGP4 | corresponding workspaces and Home |
| Groups.io | `DesktopParityPlatform` schema-2 store/client/outbox | Groups workspace and notifications |
| Rotator | `DesktopRotatorController` | Rotator, Satellite prepared targets, Global Stop |
| Configuration | `DesktopConfigurationManager` | all owner safe sections |
| Credentials | platform `CredentialVault` | Wavelog and Groups.io alias resolution only |
| Presets, alerts, notifications | `DesktopParityPlatform` | Presets, Settings, foreground/native notification adapter |
| Health/support | `DesktopApplication` and `SupportBundle` | Health/About; bounded owner metadata only |

Owner invariants:

- Selecting a radio, rotator, preset, spot, pass, calendar item, or workspace never connects, moves, transmits, or logs.
- `DesktopApplication::globalStop()` is the only fan-out entry point and is idempotent.
- Transmit and movement acceptance are profile state established by external physical acceptance, never a normal settings toggle.
- Provider clients update last-good caches; Home, DX, Band Maps, Intelligence, Portable, Operations, and Satellite only consume those owners.

