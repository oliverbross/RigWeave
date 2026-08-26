# Desktop Official Layouts v2

Normal operation uses authored, locked layouts. Panel titles remain useful
region labels, but drag handles, per-panel reset, z-order actions, cursor
changes, and the grid are absent. The same default geometry is the source for
first launch and Reset Workspace Layout.

| Workspace | Official wide composition | Compact/minimum behaviour | Large behaviour |
|---|---|---|---|
| Home | truth rail; station instruments / dominant map / activity rail | sidebar compacts; rails retain minimums; map keeps priority | map consumes surplus width |
| Radio | connection rail; backend/profile rail; VFO console; operating strip | sidebar compacts; console retains 620 px minimum | VFO and spots expand |
| Digi | truth rail; route rail / decode evidence / sequence rail; macro strip | route/sequence minimums enforced | decode evidence receives surplus |
| Panadapter | safety, source, inspector; dominant unscrolled instrument | controls remain compact; instrument minimum 260 px | instrument receives all surplus height |
| EQ | capability rail; readback/draft, plots, eight bands, action row | plots cap height; bands keep 260 px minimum | bands and plots grow without narrow faders |
| Logbook | filter/action rail; table; paging/status | table keeps semantic widths and horizontal movement | table dominates |
| Intelligence | KPI strip; tabbed explorer/map | tab content retains a bounded viewport | chart/map receives surplus |
| Sync | authority, binding, actions, conflict review | safe form order is preserved | review region expands |
| Contest | safety, session/macro strip, staging log and score | logging controls retain order | log receives surplus |
| Band Maps | compact controls plus vertical/horizontal/grid/single renderer | renderer minimums and selected band preserved | more bands/inspector space |
| Presets | search/list and selected detail | master/detail minimums | detail receives surplus |
| DX | wide feed and intelligence/RF inspector | inspector remains collapsible/adapted | feed and geography expand |
| Portable | programme/filter rail, activity/map, selected detail | stable minimum panes | map/list receives surplus |
| Operations | planner/satellite/QO-100 tabs, results, detail | tab order remains reachable | planner/map grows |
| Groups.io | groups / threads / message-detail | stable three-pane minimums | reading pane grows |
| Rotator | connection/actions, compass/telemetry, targets/presets | Stop stays visible | compass and telemetry grow |
| Settings | searchable categories / content | category list retains 230 px | detail grows |
| Health | two/three diagnostic columns plus actions | adaptive bounded cards | three columns |
| About | identity / licences; privacy/product details | readable stacking through bounded panels | acknowledgement measure stays bounded |
| Shack | full-window clock/frequency/status and Global Stop | no edit controls | no configuration controls |

Official layout geometry is expressed against the current workspace bounds.
Custom v2 geometry is stored with layout version 2 and x/y/width/height ratios;
raw pixels remain only for migration from v1. Unknown future versions fall back
to official defaults.

