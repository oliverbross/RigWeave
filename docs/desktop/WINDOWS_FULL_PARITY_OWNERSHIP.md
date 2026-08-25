# Windows Full Parity Ownership

## Authority graph

`DesktopApplication` is the only composition root.

| Concern | Sole desktop authority | Consumers |
|---|---|---|
| QSO mutation and schema 16 | `QsoDatabase` | Logbook, Fast Entry, ADIF, Wavelog |
| Wavelog queue/network | `WavelogSyncEngine` | Sync, Health, Fast Entry enqueue |
| Cluster connection and spots | `ClusterClient` + one `SpotRepository` | Home, DX, Band Maps |
| Radio lifecycle | `DesktopRadioController` | Header, Radio, shutdown |
| Rotator lifecycle | `DesktopRotatorController` | Header, Rotator, global Stop |
| Panadapter/audio lifecycle | `DesktopPanadapterController` | Panadapter, global Stop |
| Credentials | `DesktopCredentialVault` | Wavelog alias resolution only |
| Provider requests/caches | `DesktopParityPlatform` | Home, Settings, feature pages |
| Feature-domain stores | `DesktopParityPlatform` | Neural, Digi, Contest, Groups, DX Chaser |
| Support bundle | `DesktopSupportBundle` | Health/support export |

The parity platform does not create a second QSO, cluster, radio, rotator, panadapter, credential, or Wavelog owner. Feature stores contain feature-specific state and staging data; canonical QSO writes still pass through `QsoDatabase`.

## Lifecycle rules

- Restore is disconnected, RX-only and automation-disarmed.
- Unknown capability remains unavailable.
- Provider refresh is explicit and bounded; providers are disabled by default.
- Contest merge, Groups.io post, satellite selection and spot selection create review state only.
- Global Stop aborts provider requests, clears reviews, stops receive audio, and sends rotator Stop only when its controller is connected.
- Shutdown closes async owners before configuration/logging teardown.
