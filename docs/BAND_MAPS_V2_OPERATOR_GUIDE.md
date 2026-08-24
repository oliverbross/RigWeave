# Band Maps v2 operator guide

`Multi Horizontal` stacks horizontal frequency rails. `Multi Vertical` places classic vertical ladders side by side. `Grid Overview` shows every selected band; `Single Expanded` gives one detailed rail with RX, TX/split and passband truth.

Ladder labels contain the callsign only; exact frequency is carried by the calibrated marker/axis and remains explicit in row and selected-spot detail views. Age, bearing, distance, mode, spotter, source and SNR are optional metadata. CS colours the callsign and DS colours its frequency marker using the operator palette configured in Settings. CS and DS are independently multi-selectable in Band Map filters and persist without changing a preset's ranking logic.

Pinch, mouse-wheel/trackpad and direct drag use the pointer focal frequency. Viewports persist per band and can be reset. Collision placement is deterministic, density-aware, bounded to six lanes and exposes exact stack membership. Compact/standard/wide sizing trades detail against bands visible; spot text supports 9/11/13/16 sp and frequency labels can appear every tick, every second tick or every fifth tick.

The band selector is deliberately 160m–23cm. The frequency rail is segmented by the selected Region 1/2/3 display guidance: CW amber, data cyan, SSB/phone green and FM/repeater magenta where present. This is operating guidance, not a national licence authority. The jurisdiction selector distinguishes station profile, IARU guidance, reviewed national plan and custom plan.

`Contest S&P` is visible only during an active Contest session. `Vertical Band Map` defaults off and has a visible toggle at the top of a sufficiently wide Radio screen as well as in Settings. It uses the same controller/snapshot in the left 30%, shows active-band RX/TX/split markers and offers receive review only.

Sweep 3 moves source submission above destination composition, so Radio and Contest populate before the Band Maps workspace is opened. Diagnostics expose counts at source/repository/filter/display stages; empty states explain disconnected, no spots, unsupported/all-filtered and degraded cases. Filter dialog provides Reset Filters. Settings persistence is debounced and reports Saving, Saved/time or Save failed/Retry.
