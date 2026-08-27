# Android Scanner Intelligence v3

The existing receive-only scanner remains the sole bank, journal, timing and Stop owner. V4 adds an ordering callback backed by Spectrum Survey and enriched `ScanMemory` metadata: name, group, expected CTCSS/DCS, scan-enabled, priority, note, Maidenhead grid, last-heard and activity score.

Operator-selectable orders are memory order, frequency, most active, recent activity and least recently checked. Adaptive dwell is off by default. Conservative mode may extend a dwell for priority/historical activity but never shortens the operator minimum and never exceeds twice that minimum. Global Stop remains immediate and active scan state never restores.

Memories export/import bounded JSON or RFC-style quoted CSV with validation and a 2,000-row cap. Band stacks support named per-mode entries, forward/reverse cycling, last-heard state and explicit replace; no implicit physical-radio action is introduced by storage.

Historical priority is a receive scheduling hint, not proof that a channel is currently occupied.
