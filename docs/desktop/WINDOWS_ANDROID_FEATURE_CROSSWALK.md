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
| Home/HamClock modules | typed cache/module owner, `HomePage`, `ShackDisplay` | Equivalent source owner; live providers pending |
| Neural DX/Outlook | empirical schema-5 owner and `neuralOpportunities` | Equivalent evidence/calibration source owner |
| KX3/KX2, Flex, QMX/QMX+, RGO V6 | native adapters under `DesktopRadioController` | Equivalent fail-closed source owner; hardware pending |
| Non-Hamlib rotator protocols | native adapters under `DesktopRotatorController` | Equivalent fail-closed source owner; movement pending |
| Digi and DX Chaser | linked Rust modem, exact-route session and attempt store | Equivalent RX source owner; TX locked |
| Keyer | `DesktopKeyerController` tokens/preview/stopped queue | Equivalent safe source owner; send locked |
| Contest/N1MM | schema-2 session/scoring and typed packet policy | Equivalent safe source owner; trusted live peer pending |
| Band Maps | shared spots and canonical evaluator | Equivalent source owner and accepted four layouts |
| Intelligence/awards/maps | paged QSO/RF projections and local estimates | Equivalent source owner |
| Portable | cached catalogue/activity owner | Equivalent source owner; live providers pending |
| Operations/Satellite/QO-100 | cached planner plus shared SGP4 | Equivalent receive-only source owner |
| Groups.io | vault-bound foreground client and schema-2 archive/outbox | Equivalent source owner; authenticated account pending |
| Presets/settings/alerts | CRUD/review owners and native/in-app alert abstraction | Equivalent source owner; hosted notification acceptance pending |

Windows does not import Android private databases or credentials. Cross-platform compatibility is maintained through schemas, fixtures, shared core contracts, and regression builds—not by copying protected data.

## Flightline presentation crosswalk

The unlocked 41-screen tablet atlas now has a desktop adaptation record for every captured path. Desktop uses the tablet hierarchy and status semantics while replacing fixed columns with panes, adding compact/expanded icon navigation, keyboard/pointer access, platform menus and high-DPI profiles. Raw private tablet screenshots are not committed. UI correspondence is not evidence of Android-equivalent backend behavior.
