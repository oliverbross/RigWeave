# Windows Full Parity Matrix

> Deep Convergence v2 changes desktop navigation, official-layout policy and visual hierarchy only. It does not change any service-owner or capability verdict below; see `docs/ui/DESKTOP_SCREEN_CONTENT_PARITY_V2.md` for the v2 screen/content audit.

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
| Home/HamClock | FOUNDATION_WIRED | 17-module registry and Shack layout | production module controllers incomplete |
| Neural DX/Empirical Outlook | FOUNDATION_WIRED | schema-5 store, fixture model, scale data | live empirical pipeline incomplete |
| Native radio profiles | FOUNDATION_WIRED | KX3/KX2, Flex, QMX, RGO ONE surfaces | native protocol owners not bound |
| Native rotator protocols | FOUNDATION_WIRED | GS-232/EasyComm/DCU/SPID/rotctld surfaces | protocol controllers not bound |
| Digi engines | FOUNDATION_WIRED | mode registry, schema-2 store | modem/audio session integration incomplete |
| DX Chaser | FOUNDATION_WIRED | schema-1 store and receive review | timing/TX engine intentionally unavailable |
| CW/Voice Keyer | FOUNDATION_WIRED | macro registry and safe UI | audio/CAT/PTT execution incomplete |
| Intelligence/Awards/Contact Map | FOUNDATION_WIRED | summary/filter surfaces | award engines and full map incomplete |
| Contest/N1MM | FOUNDATION_WIRED | schema-2 staging and merge review | scoring/N1MM production controller incomplete |
| Intelligent Band Maps | FOUNDATION_WIRED | four layouts and shared spots | Android evaluator/controller incomplete |
| DX workspace | FOUNDATION_WIRED | shared spots and Neural view | full multi-tab Android behavior incomplete |
| Portable | FOUNDATION_WIRED | activity model and review UI | activation/chase service incomplete |
| Operations planner | FOUNDATION_WIRED | routed planner/calendar view | provider/calendar integration incomplete |
| Satellite/QO-100 | FOUNDATION_WIRED | local fixture passes and RX preview | live TLE/SGP4 controller and Doppler incomplete |
| Groups.io | FOUNDATION_WIRED | schema-2/FTS5 archive and draft review | PROVIDER_BLOCKED; delivery ambiguity engine incomplete |
| Presets/alerts/notifications | FOUNDATION_WIRED | routed configuration surfaces | production actions and Windows notifications incomplete |
| EQ Studio | FOUNDATION_WIRED | desktop EQ page | native radio capability/readback integration incomplete |

Totals: **14 `SOURCE_COMPLETE`; 17 `FOUNDATION_WIRED`; 31 audited rows**. Provider and licence blockers are annotations rather than inflated completion statuses. Because 17 core rows remain foundations, the programme's PASS rule is not met.

## Flightline UI addendum

All 19 destinations now have canonical command IDs, original SVG icons, expanded/compact navigation, menu and command-palette reachability, and deterministic gallery coverage. This closes presentation/reachability gaps only. The totals and every functional status above remain unchanged; see `docs/ui/DESKTOP_WORKSPACE_CONVERGENCE_MATRIX.md` for the UI-only matrix.
