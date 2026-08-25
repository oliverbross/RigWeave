# Windows UI Gallery

The application provides a deterministic, isolated `--gallery-dir` mode. It never reads production credentials or databases, never reaches an external network, and never enables transmit or rotator movement. TCI connection frames use an in-process localhost-only deterministic WebSocket fixture; they are fake-server visual evidence, not physical-radio evidence.

Local gallery root: `build/evidence/ui-gallery`.

| Profile | Frames | Unique SHA-256 images | Runtime warnings |
|---|---:|---:|---:|
| 1366 × 768 | 39 | Pending final artifact | Pending final artifact |
| 1920 × 1080 | 39 | Pending final artifact | Pending final artifact |
| 2560 × 1440 | 39 | Pending final artifact | Pending final artifact |
| 150% scale (1280 × 720 logical / 1920 × 1080 physical) | 39 | 39 | 0 |

Frames cover Home, Shack, native/generic Radio, disconnected/connected/two-receiver/switch TCI states, Digi, five Panadapter/waterfall states including two concurrent receivers, EQ, Logbook, flat RF filters and observed heat, three globe states including selected path, stale/offline empty state, Sync, Contest, four Band Map layouts, Presets, DX, Portable, Planner/Satellite/QO-100, Groups.io, Rotator, Settings, Health and About.

The exact-SHA workflows require 39 PNG files per profile and reject blank/solid or unexpectedly transparent captures. Gallery evidence proves reachability and deterministic rendering, not complete production behavior, authenticated live-service acceptance, audio behavior, physical radio compatibility, TX, or RF behavior.
