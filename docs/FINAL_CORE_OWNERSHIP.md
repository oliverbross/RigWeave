# Final Core Ownership

| Contract | Single owner | Consumers |
|---|---|---|
| Operating context | `OperatingContextAuthority` | Home, HamClock, Neural, Sync, Digi and workspace handoffs |
| QSO persistence | QSO controller/database | Log, Wavelog projection, intelligence |
| Wavelog state | `WavelogController` / Apple `WavelogSync` | Sync and health surfaces |
| Groups.io state | `GroupsIoController` | Groups workspace, Home and health |
| Digi sessions | `DigiController` | Digi workspace and health |
| Satellite operations | `SatelliteOperationsController` | Android Operations/HamClock; Apple reports platform gap |
| Route selection | `WorkspaceActionRouter` / Apple `WorkspaceAction` dispatcher | Navigation only |
| Configuration recovery | `ConfigurationRecovery` | Android Settings |

No consumer owns a duplicate provider, cache, transmit latch, or persistence layer. A missing owner is rendered unavailable rather than fabricated.
