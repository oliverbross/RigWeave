# Desktop Function Reachability v2

Every enabled action routes through the canonical command registry or the
existing QML controller/service owner. Decorative enabled controls are not
permitted.

| Control group | Action/owner | Result and states | Test/evidence | Status |
|---|---|---|---|---|
| 19 sidebar destinations | `nav.*` → `Desktop.setCurrentDestination` | Selected, hover, focus, collapsed tooltip | UI contract + gallery | FUNCTIONAL |
| Native Navigate menu / palette | canonical registry | Same destination and enabled truth | UI contract | FUNCTIONAL |
| Edit Layout / Done | `view.editLayout` → `editLayoutMode` | Checked state, banner, handles, Escape dismissal | UI contract + edit galleries | FUNCTIONAL |
| Reset official layout | `view.resetLayout` | Clears custom geometry, exits edit mode | UI contract + stress | FUNCTIONAL |
| Panel move/resize | `CanvasPanel` | 8 px snap, bounds, minimums, overlap rejection, proportional save | UI contract + stress | FUNCTIONAL |
| Sidebar collapse | `sidebarCollapsed` | Manual persistence and automatic compact mode | UI contract + stress | FUNCTIONAL |
| Global Stop / Escape | `radio.stop` | Radio/Parity/Panadapter/Rotator safe stop | safety tests + stress | FUNCTIONAL |
| Home map/list handoffs | RF model / `nav.*` | Observational selection and explicit destination handoff | RF tests + gallery | READ_ONLY_FUNCTIONAL |
| Radio disconnect / RX apply | Radio owner | Explicit, connected/capability gated, error through owner | radio/TCI tests | LIVE_ACCEPTANCE_PENDING |
| Digi prepare / dry run | Parity review owner | Visible but disabled until a validated route is source-complete; no direct TX | parity tests + gallery | LIVE_ACCEPTANCE_PENDING |
| Panadapter controls | Panadapter owner | Start/stop/configuration/empty/health | TCI + scale tests | FUNCTIONAL |
| EQ record/apply | No accepted desktop owner | Disabled with exact reason | gallery + source | BLOCKED |
| Logbook fast entry/import/export/filter/page | Desktop/ADIF/QSO owners | Validation, progress, cancel, I/O failure | data tests | FUNCTIONAL |
| Intelligence filters/map/handoffs | RF/QSO owners | Empty/filter/selection states | RF tests | READ_ONLY_FUNCTIONAL |
| Sync configure/actions/conflict review | Vault/Wavelog/QSO owners | Busy/success/error/ambiguous-write truth | network/data tests | LIVE_ACCEPTANCE_PENDING |
| Contest/Band Maps/Presets/DX | Parity/Spots/Radio review | Explicit prepare/review; no blind CAT | parity tests | READ_ONLY_FUNCTIONAL |
| Portable/Operations/Groups | Parity review/draft owners | Provider empty/error; nothing posts/tunes automatically | parity tests | READ_ONLY_FUNCTIONAL |
| Rotator Stop/targets | Rotator owner | Inert restore; capability gate; prominent Stop | safety tests | LIVE_ACCEPTANCE_PENDING |
| Settings export/import preview | DesktopConfig | Safe exclusions, preview, I/O error | platform tests | FUNCTIONAL |
| Health/support/about/licences | Desktop/Support/build info | Refresh, support ZIP, packaged notices | platform/package tests | FUNCTIONAL |

Totals: 71 control groups — 47 functional, 16 read-only functional, 5
live-acceptance pending, and 3 blocked/disabled. Enabled decorative controls:
0. Every blocked control names the missing owner or acceptance gate.

## Functional Parity Closure v1 addendum

All 17 former foundation workspaces now bind to typed production owners or explicit reviewed actions. The five live-acceptance and three disabled groups remain intentionally blocked for credentials, hardware readback, transmission or movement; they are not decorative controls.
