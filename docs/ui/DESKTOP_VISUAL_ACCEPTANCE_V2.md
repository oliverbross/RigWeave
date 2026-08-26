# Desktop Visual Acceptance v2

## Evidence

- Tablet reference: 41 private indexed captures; no new tablet capture because
  no authorised device was connected.
- macOS before: accepted five-profile, 39-frame v1 gallery at `9c0e3d6`.
- macOS after: v2 normal and Edit Layout galleries under
  `build/evidence/v2-gallery/`.
- Windows before: accepted v1 hosted gallery.
- Windows after: pending a Windows build or hosted exact-SHA run.
- Comparison sheets: `build/evidence/v2-comparisons/`.

The gallery validator rejects blank/solid, transparent, or unsaved frames. It
does not assert pixel identity across platforms.

## Review result

The bounded first review found two v2 defects: EQ plots consumed the band
region, and the compact Panadapter safety text clipped. Both were corrected in
one batch. The confirmation gallery is the second and final polishing round.

| Workspace | Before | After | Remaining visual issue |
|---|---:|---:|---|
| Home | 17 | 27 | Private live weather/solar data unavailable in deterministic desktop owner |
| Radio | 16 | 27 | Physical native readback and meter rendering pending |
| Digi | 17 | 26 | Production decoder/waterfall owner unavailable |
| Panadapter | 23 | 28 | Physical audio/IQ route pending |
| EQ | 18 | 26 | Real readback/sample/apply owner pending |
| Logbook | 22 | 26 | Empty deterministic database is intentionally sparse |
| Intelligence | 20 | 25 | Several Android-only tabs are truthful foundations |
| Sync | 21 | 25 | Authenticated queue/conflict population pending |
| Contest | 20 | 25 | Live session/network population pending |
| Band Maps | 22 | 26 | Physical radio handoff pending |
| Presets / DX | 20 | 25 | Native preset CAT application remains review-only |
| Portable / Operations | 20 | 25 | Live provider/pass population pending |
| Groups.io | 21 | 25 | Authenticated message population pending |
| Rotator | 22 | 26 | Movement acceptance pending |
| Settings / Health / About | 21 | 26 | Windows typography/access-key visual acceptance pending |

macOS deterministic source/visual acceptance passes. Overall programme verdict
cannot be PASS until Windows v2 build/interaction evidence and the remaining
cross-platform final gates complete.
