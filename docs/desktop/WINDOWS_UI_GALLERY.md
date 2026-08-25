# Windows UI Gallery

The application provides a deterministic, isolated `--gallery-dir` mode. It never reads production credentials or databases, never connects, and never enables transmit or rotator movement.

Local gallery root: `build/evidence/ui-gallery`.

| Profile | Frames | Unique SHA-256 images | Runtime warnings |
|---|---:|---:|---:|
| 1920 × 1080 | 25 | 25 | 0 |
| 2560 × 1440 | 25 | 25 | 0 |
| 150% scale (1920 × 1080 logical) | 25 | 25 | 0 |

Frames cover Home, Shack, native/generic Radio, Digi, Panadapter, EQ, Logbook, Intelligence, Sync, Contest, four Band Map layouts, DX, Portable, Planner/Satellite/QO-100, Groups.io, Rotator, Settings, Health and About.

The exact-SHA Windows and macOS workflows regenerate all three profiles and require 25 PNG files per profile. Gallery evidence proves reachability and deterministic rendering, not complete production behavior or live-service acceptance.
