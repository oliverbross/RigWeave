# Desktop Functional Parity Matrix

Source baseline: `de32c8ac908c7979f39bfdfc41ca050378901e75`. This matrix separates source completion from hosted, authenticated, visual and physical acceptance. `SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING` is source-complete and deliberately fail-closed where real credentials, audio, CAT, RF, transmission or movement are required.

| Area | Status | Production owner and evidence boundary |
|---|---|---|
| Navigation and shell | SOURCE_COMPLETE | Canonical command registry, native menus and 19 workspaces |
| Global Stop and safe restore | SOURCE_COMPLETE | One application fan-out; disconnected, stopped, disarmed restore |
| Configuration and credentials | SOURCE_COMPLETE | Atomic versioned sections and alias-only platform vault |
| Canonical QSO authority | SOURCE_COMPLETE | Schema-16 SQLite mutation/projection owner |
| Logbook and Fast Entry | SOURCE_COMPLETE | Paged model and canonical save path |
| ADIF import/export | SOURCE_COMPLETE | Bounded asynchronous service |
| Wavelog and Sync | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | One outbox/sync owner; authenticated tenant pending |
| Cluster and shared spots | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | One transport and shared repository; live cluster pending |
| Generic Hamlib radio | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | Pinned controller; physical CAT/PTT/TUNE pending |
| Hamlib rotator | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | One controller and Stop; physical movement pending |
| Panadapter receive path | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | Owned I/Q/audio/DSP lifecycle; physical routes pending |
| Provider/cache platform | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | Bounded clients and last-good policy; live providers pending |
| Domain databases/migrations | SOURCE_COMPLETE | Isolated versioned stores and migration tests |
| Gallery/Health/About | SOURCE_COMPLETE | Deterministic gallery and bounded health/support surfaces |
| Home/HamClock | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | Typed owner snapshots and bounded provider cache |
| Neural DX/Empirical Outlook | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | Schema-5 empirical evidence/calibration pipeline |
| Native radio profiles | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | KX/KX2, Flex, QMX/QMX+ and proven RGO V6 adapters |
| Native rotator protocols | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | GS-232, EasyComm and rotctld serial/TCP adapters |
| Digi engines | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | Linked Rust modem, exact-route RX sessions and decode store; TX locked |
| DX Chaser | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | Local-decode eligibility, dry run and attempt journal; TX locked |
| CW/Voice Keyer | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | Typed tokens, local preview and stopped queue; send locked |
| Intelligence/Awards/Contact Map | SOURCE_COMPLETE | Paged canonical QSO/RF projections and local estimates |
| Contest/N1MM | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | Schema-2 staging/scoring and typed bounded packet policy; live peer pending |
| Intelligent Band Maps | SOURCE_COMPLETE | Shared-spot canonical evaluator and four accepted layouts |
| DX workspace | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | Shared projections over spot/provider/outlook owners |
| Portable | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | Cached provider/catalogue projections and reviewed handoff |
| Operations planner | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | Cached calendar/catalogue owner and stable spatial query |
| Satellite/QO-100 | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | Shared SGP4 pass owner and receive-only handoff |
| Groups.io | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | Vault-bound foreground client, schema-2 archive, outbox and reconciliation |
| Presets/alerts/notifications | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | CRUD/review owners and native/in-app quiet notification policy |
| EQ Studio | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | Eight-band draft/review owner; physical readback/apply locked |

Totals: 31 audited rows; 31 source-complete; `FOUNDATION_WIRED = 0`; `MISSING = 0`. External acceptance annotations are not counted as missing source.
