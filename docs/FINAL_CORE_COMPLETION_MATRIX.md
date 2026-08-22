# Final Core Completion Matrix

| Area | Source status | Evidence / remaining boundary |
|---|---|---|
| Shared operating context | Complete | Generation-stamped authority and stale rejection tests |
| Typed workspace routing | Complete | Exact-route tests; all side-effect flags false |
| Configuration recovery | Complete on Android | Hash, preview, selective restore, rollback and instrumentation tests; Apple import is not claimed |
| System Health / support bundle | Complete on Android | Fixed schema truth and metadata-only ZIP |
| Wavelog | Source complete | Existing fake transport/cache suites; authenticated live acceptance pending |
| Groups.io | Source complete | All-groups local recent summary/FTS; authenticated live acceptance pending |
| Digi | Source complete for existing modes | Schema 2 durable sessions; RTTY/PSK limitations remain labelled |
| Neural DX / HamClock | Source complete | Empirical observations only; no P.533 claim |
| Apple destinations | Complete | Unsigned generic-device build required; orbital pass engine remains a platform gap |
| Release | Candidate only | No install, signing, deployment, store submission or RF validation performed |

Fixed schema truth: QSO 13, projection 2, Neural 5, Digi 2, Groups.io 2. P.533 remains `LICENSE_BLOCKED`.

## Bounded backlog

1. **Release blocker:** run the migration instrumentation matrix on a hosted emulator.
2. **Release blocker:** complete protected-device and authenticated-service acceptance.
3. **Release blocker:** resolve Neural-DX-Watcher licence/permission before any derived distribution claim.
4. **Post-release core:** add explicit QSO 7–12 individual fixtures beyond the existing 6→13 superset migration.
5. **Platform parity:** provide one shared Apple configuration import/export implementation.
6. **Platform parity:** expose the existing pinned SGP4 bridge to Apple Operations without a second orbital model.
7. **Platform parity:** add Apple Digi companion state when a real durable owner exists.
8. **Optional:** APRS.
9. **Optional:** Winlink.
10. **Optional:** WWBOTA.
11. **Provider blocked:** weather radar/cloud/hazard layers.
12. **Licence blocked:** local P.533.
13. **Platform future:** desktop shell.
14. **Excluded:** additional Nexus modes, modem/radio stack, notch/compressor and WinKeyer.
