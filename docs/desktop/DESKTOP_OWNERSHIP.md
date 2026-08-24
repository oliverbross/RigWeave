# Desktop ownership and lifecycle

## Owned paths

The branch owns `desktop/**`, `docs/desktop/**`, `cmake/desktop/**`, the two desktop workflows, additive root `CMakePresets.json`, and the narrow compiler/PIC correction in `core/CMakeLists.txt`. It adds no Android, iOS or mobile database change.

## One service graph

`DesktopApplication` constructs one instance of each service: paths, configuration, credential vault, canonical QSO database, logbook model, ADIF service, spot repository, cluster controller, Wavelog engine, Hamlib registry/radio, Hamlib rotator, panadapter and support bundle. QML receives objects and immutable values; it never creates providers, database connections, radios or worker threads.

| Authority | Singular owner | Consumers |
|---|---|---|
| Canonical desktop QSO | `QsoDatabase` | Logbook, ADIF, Wavelog, Intelligence, Home |
| Remote QSO mutation | `WavelogSyncEngine` outbox | Sync page and local mutation coordinator |
| Cluster connection/observations | `ClusterController` + `SpotRepository` | DX, Band Maps, Home, later Radio context |
| Radio CAT | `DesktopRadioController` | Radio and explicit receive-review |
| Rotator movement | `DesktopRotatorController` | Rotator and prepared targets |
| Audio I/Q | `DesktopPanadapter` | Panadapter only |
| Credentials | `DesktopCredentialVault` | Wavelog and later account foundations |

## Startup and shutdown

Startup creates local paths, bounded logging, safe configuration and the local database. It does not connect a provider/radio/rotator, start audio, restore PTT/TUNE, arm automation, move hardware or log a QSO.

Shutdown order is: cancel ADIF, stop audio, request rotator STOP, disconnect rotator, disconnect radio, disconnect cluster, save safe configuration, close services/database, then close logging. `Esc` returns focus/navigation; global `Ctrl+Shift+S` is a distinct safety action.
