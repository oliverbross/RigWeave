# Windows–Android Feature Crosswalk

Android remains the behavior inventory. Sharing a C++ core or displaying the same label is not counted as parity by itself.

| Android concern | Windows owner/surface | Crosswalk result |
|---|---|---|
| QSO mutation/projection | `QsoDatabase`, `DesktopApplication::saveFastEntry` | Equivalent source owner |
| Wavelog queue/binding | `WavelogSyncEngine` | Equivalent source owner; authentication pending |
| DX cluster/spot repository | `ClusterClient`, `SpotRepository` | Equivalent source owner; live network pending |
| Hamlib generic CAT | `DesktopRadioController` | Equivalent source owner; hardware pending |
| Rotator Hamlib lifecycle | `DesktopRotatorController` | Equivalent source owner; movement pending |
| Panadapter lifecycle | `DesktopPanadapterController` | Equivalent receive source; physical audio pending |
| Home/HamClock modules | `Parity.homeModules`, `HomePage`, `ShackDisplay` | Registry/layout crosswalk only |
| Neural DX/Outlook | `Parity.neuralOpportunities`, `neural-dx.sqlite` | Data contract foundation |
| KX3/KX2, Flex, QMX, RGO ONE | `RadioPage` profiles | UI/capability truth only; native adapters absent |
| Non-Hamlib rotator protocols | `RotatorPage` protocol list | UI truth only; adapters absent |
| Digi and DX Chaser | `DigiPage`, Digi/DX Chaser stores | Registry/storage foundation |
| Keyer | `Parity.keyerMacros`, radio surface | Macro foundation; execution absent |
| Contest/N1MM | `ContestPage`, contest store, merge review | Staging foundation; protocol/scoring incomplete |
| Band Maps | `BandMapsPage`, shared spots | Four layouts; evaluator integration incomplete |
| Intelligence/awards/maps | `IntelligencePage`, QSO summaries | Summary foundation; full engines incomplete |
| Portable | `PortablePage`, portable model | Fixture/review foundation |
| Operations/Satellite/QO-100 | `OperationsPage`, satellite model | Local RX-preview foundation |
| Groups.io | `GroupsPage`, SQLite/FTS5 archive | Offline archive/draft foundation |
| Presets/settings/alerts | routed QML and desktop configuration | Partial configuration crosswalk |

Windows does not import Android private databases or credentials. Cross-platform compatibility is maintained through schemas, fixtures, shared core contracts, and regression builds—not by copying protected data.

## Flightline presentation crosswalk

The unlocked 41-screen tablet atlas now has a desktop adaptation record for every captured path. Desktop uses the tablet hierarchy and status semantics while replacing fixed columns with panes, adding compact/expanded icon navigation, keyboard/pointer access, platform menus and high-DPI profiles. Raw private tablet screenshots are not committed. UI correspondence is not evidence of Android-equivalent backend behavior.
