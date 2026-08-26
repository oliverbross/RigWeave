# Windows Full Parity Matrix

> Functional Parity Closure v1 supersedes the Deep Convergence v2 foundation verdicts below. The authoritative 31-row result is `DESKTOP_FUNCTIONAL_PARITY_MATRIX.md`; live and physical acceptance remain separate.

Status vocabulary:

- `SOURCE_COMPLETE`: production desktop owner/service is present and locally tested.
- `FOUNDATION_WIRED`: routed UI, typed model/store or reviewed action exists, but Android-equivalent production behavior is incomplete.
- `PROVIDER_BLOCKED`: external access or authenticated live acceptance remains unavailable.
- `LICENCE_BLOCKED`: required distributable data is not legally available in this branch.

| Area | Status | Desktop evidence | Remaining blocker |
|---|---|---|---|
| Navigation and shell | SOURCE_COMPLETE | 19 destinations, palette, menus, focus, Shack | Windows live interaction pending |
| Global Stop and safe restore | SOURCE_COMPLETE | disconnected/disarmed startup; Escape and Stop fan-out | Physical hardware proof pending |
| Configuration and credentials | SOURCE_COMPLETE | atomic config, alias-only vault access | Windows Credential Manager live proof pending |
| Canonical QSO authority | SOURCE_COMPLETE | schema-16 SQLite owner | none in source |
| Logbook and Fast Entry | SOURCE_COMPLETE | paged model, save path, projection tests | visual/operator acceptance pending |
| ADIF import/export | SOURCE_COMPLETE | bounded async service | large real-file acceptance pending |
| Wavelog and Sync | SOURCE_COMPLETE | one engine and alias resolver | PROVIDER_BLOCKED for authenticated service |
| Cluster and shared spots | SOURCE_COMPLETE | one cluster owner/repository | PROVIDER_BLOCKED for live cluster |
| Generic Hamlib radio | SOURCE_COMPLETE | pinned static Hamlib controller | physical CAT pending |
| Hamlib rotator | SOURCE_COMPLETE | one rotator owner and Stop | physical movement pending |
| Panadapter receive path | SOURCE_COMPLETE | owned receive/audio/DSP lifecycle | physical I/Q and audio pending |
| Provider/cache platform | SOURCE_COMPLETE | bounded policy and fake-response tests | individual providers disabled by default |
| Domain databases/migrations | SOURCE_COMPLETE | five isolated versioned stores | Android-to-desktop data import not required |
| Gallery/Health/About | SOURCE_COMPLETE | 75 distinct frames, health and build truth | Windows hosted frames pending |
| Home/HamClock | SOURCE_COMPLETE | typed module/cache owners and Shack layout | live provider acceptance pending |
| Neural DX/Empirical Outlook | SOURCE_COMPLETE | schema-5 empirical evidence/calibration pipeline | live evidence acceptance pending |
| Native radio profiles | SOURCE_COMPLETE | KX/KX2, Flex, QMX/QMX+, proven RGO V6 adapters | physical CAT acceptance pending |
| Native rotator protocols | SOURCE_COMPLETE | GS-232/EasyComm/rotctld serial/TCP owners | physical movement acceptance pending |
| Digi engines | SOURCE_COMPLETE | linked Rust modem, exact-route audio session and schema-2 store | physical audio/TX acceptance pending |
| DX Chaser | SOURCE_COMPLETE | eligibility reducer, dry run and attempt journal | TX locked pending acceptance |
| CW/Voice Keyer | SOURCE_COMPLETE | typed tokens, local preview and stopped queue | send locked pending acceptance |
| Intelligence/Awards/Contact Map | SOURCE_COMPLETE | paged canonical-QSO and RF projections | none in source |
| Contest/N1MM | SOURCE_COMPLETE | schema-2 staging/scoring and typed bounded packet policy | live trusted peer pending |
| Intelligent Band Maps | SOURCE_COMPLETE | four layouts over shared canonical evaluator | none in source |
| DX workspace | SOURCE_COMPLETE | spot/provider/outlook projections | live providers pending |
| Portable | SOURCE_COMPLETE | cached catalogues and reviewed activity handoff | live providers pending |
| Operations planner | SOURCE_COMPLETE | cached calendar/catalogue and spatial query owner | live providers pending |
| Satellite/QO-100 | SOURCE_COMPLETE | local SGP4 pass controller and receive-only handoff | live TLE/physical acceptance pending |
| Groups.io | SOURCE_COMPLETE | vault-bound client, schema-2 archive/outbox/reconciliation | authenticated account pending |
| Presets/alerts/notifications | SOURCE_COMPLETE | CRUD/review owners and quiet native/in-app policy | native hosted acceptance pending |
| EQ Studio | SOURCE_COMPLETE | eight-band draft/review transaction boundary | physical readback/apply pending |

Totals: **31 source-complete; 0 `FOUNDATION_WIRED`; 0 `MISSING`; 31 audited rows**. Provider, hosted and physical blockers remain explicit annotations.

## Flightline UI addendum

All 19 destinations now have canonical command IDs, original SVG icons, expanded/compact navigation, menu and command-palette reachability, and deterministic gallery coverage. This closes presentation/reachability gaps only. The totals and every functional status above remain unchanged; see `docs/ui/DESKTOP_WORKSPACE_CONVERGENCE_MATRIX.md` for the UI-only matrix.
