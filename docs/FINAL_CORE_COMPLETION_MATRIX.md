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
| Keyer/Hotkeys | Integrated on Android | One production controller; Contest typed intents; physical keyboard/audio/radio acceptance pending |
| Contest/N1MM | Integrated on Android | Schema 1, native destination, canonical QSO/Wavelog path and bounded default-off network runtime; live peer pending |
| DX Chaser | Integrated on Android | Schema 1, Digi subpage, exact local-decode eligibility and canonical outcome feedback; live FT acceptance pending |
| Neural DX / HamClock | Source complete | Empirical observations only; no P.533 claim |
| Apple destinations | Complete | Simulator and unsigned generic-device builds pass; orbital pass engine remains a platform gap |
| Release | Candidate only | No install, signing, deployment, store submission or RF validation performed |

Fixed schema truth: QSO 13, projection 2, Neural 5, Digi 2, Groups.io 2, Contest 1, DX Chaser 1. P.533 remains `LICENSE_BLOCKED`.

## Bounded backlog

Hosted Linux/KVM migration evidence: 34/34 instrumentation tests passed for QSO, projection, Neural, Digi, Groups.io and configuration-recovery paths.

1. **External acceptance:** complete protected-device and authenticated-service acceptance before deployment claims.
2. **Licence boundary:** resolve Neural-DX-Watcher licence/permission before any derived distribution claim.
3. **Post-release core:** add explicit QSO 7–12 individual fixtures beyond the existing 6→13 superset migration.
4. **Platform parity:** provide one shared Apple configuration import/export implementation.
5. **Platform parity:** expose the existing pinned SGP4 bridge to Apple Operations without a second orbital model.
6. **Platform parity:** add Apple Digi companion state when a real durable owner exists.
7. **Optional:** APRS.
8. **Optional:** Winlink.
9. **Optional:** WWBOTA.
10. **Provider blocked:** weather radar/cloud/hazard layers.
11. **Licence blocked:** local P.533.
12. **Platform future:** desktop shell.
13. **Excluded:** additional Nexus modes, modem/radio stack, notch/compressor and WinKeyer.
