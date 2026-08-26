# Desktop Reference Atlas v2

This atlas uses the unlocked Lenovo TB373FU reference set at
`build/evidence/tablet-reference-ce1b99f-20260825T213846Z/`, the accepted
macOS v1 gallery at `9c0e3d6`, the accepted v1 Windows gallery, and the v2
deterministic galleries. Private tablet screenshots remain ignored and are not
committed. The tablet was not connected during this run, so no new device
navigation or capture occurred.

| Workspace | Android source | v1 diagnosis | Official v2 decision | Windows v2 |
|---|---:|---|---|---|
| Home | 01 | Metric strip and two broad panels; no dominant map | Compact truth rail over station instruments, dominant RF map, and DX/portable/satellite rail | Pending build |
| Shack | 14 | Functional but visually isolated from Home | Full-window read-only glance surface; no layout handles or configuration | Pending build |
| Radio | 02 | Generic backend column, metric cards, large empty capability panel | Backend/profile rail, dominant observed amber VFO console, receiver truth, RX review, spots, restrained keyer | Pending build |
| Digi / Chaser | 03, 15 | One broad tab/list panel | Route/mode/session rail, dominant decode evidence, sequence/target/safety rail, macro strip | Pending build |
| Panadapter | capability hidden | Instrument was sound but buried below three large control panels | Compact source/status/inspector above dominant unscrolled spectrum/waterfall | Pending build |
| EQ | capability hidden | Music-player-like fader row with no readback/draft separation | Flightline audio bench with readback/draft truth, plot regions, eight bands, provenance, apply gate | Pending build |
| Logbook | 06, 16 | Sound table but freeform framing | Fixed query/action bar, dominant fixed-header table, paging/status rail | Pending build |
| Intelligence | 07, 17, 27 | Summary plus tab explorer; several truthful foundations | Equal KPI strip, tabbed chart/map explorer, filters and shared selected evidence | Pending build |
| Sync | 41 | Tall form and status blocks | Authority banner, binding, actions, conflicts/outbox review and history composition | Pending build |
| Contest | 04, 18–20 | Session strip plus staging list | Setup/logging/review/network cockpit using existing review owners | Pending build |
| Band Maps | 05, 21–24 | Renderer existed but all panels were always editable | Locked vertical/horizontal/grid/single layouts with compact controls and detail | Pending build |
| Presets | 08 | Reachable master/detail foundation | Compact searchable list with explicit review-only application boundary | Pending build |
| DX | 09, 25–27 | Feed and evidence separated weakly | Wide feed plus intelligence inspector and RF geography handoff | Pending build |
| Portable | 10, 28–29 | Map/list composition present | Programme/filter rail, dominant activity/map, selected detail, provider truth | Pending build |
| Operations | 11, 30–32 | Tabs present; freeform chrome dominated | Planner/Satellite/QO-100 tabs with results and selected detail | Pending build |
| Groups.io | 12, 33–34 | Master/detail foundation | Locked groups/threads/message three-pane layout with draft truth | Pending build |
| Rotator | 38 | Safe controls but generic framing | Compass-led telemetry, profile/presets/automation, prominent Stop; no movement on open | Pending build |
| Settings | 13, 35–39 | Correct master/detail but nested in movable panels | Native searchable category list and stable content region; auto-save and safety truth | Pending build |
| Health | 39 | Inconsistent freeform diagnostic cards | Two/three adaptive columns, error/action priority, bounded repair controls | Pending build |
| About | 40 | Sparse identity/licence panels | Identity/build/schema, product summary, incorporated software, providers, licences and thanks | Pending build |

## Ordering and density findings

- v1 over-weighted panel chrome and empty background; v2 locks authored geometry and removes handles from normal operation.
- Home, Radio, Digi, EQ and Panadapter required structural correction, not colour polish.
- Existing Logbook, Settings, Groups, Band Maps and operational foundations already had useful desktop compositions; the main correction is predictable locked placement and permanent navigation.
- No screenshot is service proof. Deterministic gallery state is labelled and isolated.

## Evidence paths

- Tablet index: `build/evidence/tablet-reference-ce1b99f-20260825T213846Z/`
- macOS v1: sibling v1 worktree `build/evidence/local-desktop-finish/ui-gallery-exact-final/`
- Windows v1: sibling v1 worktree `build/evidence/windows-ui-before-ce1b99f/`
- macOS v2: `build/evidence/v2-gallery/`
- Comparison sheets: `build/evidence/v2-comparisons/`

