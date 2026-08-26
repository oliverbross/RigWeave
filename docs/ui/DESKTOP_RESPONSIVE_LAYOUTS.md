# Desktop Responsive Layouts

## Shell rules

| Rule | Value | Result |
|---|---:|---|
| Safe window minimum | 1180×720 logical | Primary header and content remain reachable |
| Expanded / collapsed workspace sidebar | 224 / 52 px | Grouped primary navigation; manual state persists and compact width collapses automatically |
| Compact header breakpoint | 1360 px | Hides secondary RADIO/CLUSTER chips; written state remains in workspaces and Stop remains visible |
| Canvas panel minimum | 240×150 px default | Panels remain focusable and recoverable; specialized panels may use stricter minima |
| Ordinary controls / rows | 36 / 34 px | Compact pointer/keyboard density |

Every major module is a `CanvasPanel`, but movement, edge/corner resize and stacking are disabled in Operate mode. Explicit Edit Layout enables those actions on an 8 px grid, clamps geometry to the visible workspace, blocks overlaps and saves ratios in the safe `desktopLayouts` configuration section. Reset Workspace Layout clears only the active workspace geometry, restores authored defaults and exits Edit Layout/Shack mode. Settings category navigation and detail remain independent workspace panels.

## Deterministic profiles

- Windows: 1366×768, 1920×1080, 2560×1440, and an effective 1280×720 logical viewport rendered at 150%.
- macOS: 1440×900, 1512×982, 1920×1080, 2560×1440, and an effective 1280×720 logical viewport rendered at 150%.
- Each profile captures 58 frames: 39 operating views covering every destination and the existing TCI/panadapter/RF/Band Maps variants, plus 19 explicit Edit Layout views.

The scale soak test remains the non-visual resize/lifecycle gate. The hosted galleries are visual layout proof with deterministic private fixtures; they are not live-provider, audio, RF, hardware, or physical-display acceptance.
