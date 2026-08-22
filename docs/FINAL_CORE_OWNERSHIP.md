# Final Core Ownership

| Contract | Single owner | Consumers |
|---|---|---|
| Operating context | `OperatingContextAuthority` | Home, HamClock, Neural, Sync, Digi and workspace handoffs |
| QSO persistence | QSO controller/database | Log, Wavelog projection, intelligence |
| Wavelog state | `WavelogController` / Apple `WavelogSync` | Sync and health surfaces |
| Groups.io state | `GroupsIoController` | Groups workspace, Home and health |
| Digi sessions | `DigiController` | Digi workspace and health |
| Keyer queue/transmission | `KeyerController` / `AndroidKeyerRuntime` | Radio, physical hotkeys and Contest typed adapter |
| Contest rules/score/dupe/serial | `ContestRuntime` and merged Contest authorities | Contest workspace, DX Chaser read-only context, future Band Maps |
| N1MM network | one `N1mmNetworkController` scoped to active Contest | Contest network/review and read-only claims |
| DX Chaser | `DxChaserController` / `DxChaserStore` | Digi subpage, Health and future Band Maps |
| Satellite operations | `SatelliteOperationsController` | Android Operations/HamClock; Apple reports platform gap |
| Route selection | `WorkspaceActionRouter` / Apple `WorkspaceAction` dispatcher | Navigation only |
| Configuration recovery | `ConfigurationRecovery` | Android Settings |

No consumer owns a duplicate provider, cache, transmit latch, or persistence layer. A missing owner is rendered unavailable rather than fabricated.
