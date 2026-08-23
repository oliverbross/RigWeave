# Band automation and mechanical safety

Assignments are keyed by optional radio profile plus canonical band ID and select exactly one rotator. Policies are OFF, MANUAL, PROMPT, AUTO_SELECTED_TARGET, and SATELLITE_SESSION. Heading mode, one offset owner, bidirectional alternative, and movement-during-TX policy are explicit.

Automatic movement requires every gate: session arm, foreground, exact station/radio/band generation, permitting assignment, selected/locked unexpired target, fresh position, absolute-move capability, safe limits/path, TX permission, dwell, deadband, cooldown, no conflicting satellite session, and no manual holdoff. Defaults are 3 degrees, 2 seconds dwell, 10 seconds cooldown, 5 minutes target expiry, 5 seconds position freshness, and 180 degrees maximum move.

Geometry preserves custom ranges including -180..180 and 0..450, stable north crossings, forbidden sectors, short/long path, single-owner offsets, and bidirectional alternatives. Flip-over requires elevation, proven capability, explicit profile enablement, compatible limits, and deterministic tests.

Manual move/jog/preset and context/background/disconnect events disarm automation. Background does not park or start movement. TX blocks new movement by default. STOP bypasses queued work but success is not claimed until telemetry confirms it.
