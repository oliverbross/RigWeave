# RC1 Owner Graph

Each platform has one canonical state/mutation owner per concern. UI surfaces are projections and command emitters; they do not own durable state.

| Concern | Android owner | Apple owner | Desktop owner |
| --- | --- | --- | --- |
| QSO database/mutation/projection | `QsoDatabase` + `QsoMutationCoordinator` | `QSOStore` | `QsoDatabase` + desktop QSO owner |
| Wavelog | `WavelogController`/`WavelogSyncStore` | `WavelogSync` | `DesktopWavelogController` |
| Cluster/spots/provider cache | `ClusterController`/spot repository | `FeatureModel` services | `DesktopFunctionalOwners` |
| Neural evidence/outlook | `NeuralOutlookController` | `FeatureModel` | `DesktopFunctionalOwners` |
| Home/HamClock | `HamClockRegistries` | `FeatureModel` | `DesktopEngagementControllers` |
| Radio/Hamlib/TCI | `FlexRadioController` + platform transport | `RadioModel` | `DesktopRadioController` |
| Audio/panadapter/waterfall | audio routes + `FlexControl` | `RadioModel` | `DesktopPanadapter`/audio engine |
| Digi | `DigiController` | `FeatureModel` | desktop modem owner + `mfsk-core` |
| Keyer/Contest/N1MM/DX Chaser | canonical controller/stores | `FeatureModel` | `DesktopFunctionalOwners` |
| Band Maps/Portable/Operations/Satellite | feature controllers | `FeatureModel` | `DesktopFunctionalOwners` |
| Groups.io | `GroupsIoFeature` | `GroupsIoFeature` | desktop Groups.io owner |
| Rotator | platform rotator controller | `RadioModel` | `DesktopRotatorController` |
| Configuration/credential vault | configuration owner + Android Keystore | Keychain/configuration owner | `DesktopConfiguration` + vault |
| Alerts/health/support | notification + `SystemHealthCentre` | `FeatureModel` | desktop health owner |
| Operating context/workspace/global Stop | `MainActivity` coordinators | `ContentView`/models | `DesktopApplication` |

Owner boundaries are enforced by mutation APIs, connection state/readback, schema contracts and deterministic tests. A second UI model never establishes feature completion.
