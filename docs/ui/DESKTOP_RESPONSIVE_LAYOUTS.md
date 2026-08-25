# Desktop Responsive Layouts

## Shell rules

| Rule | Value | Result |
|---|---:|---|
| Safe window minimum | 1180×720 logical | Primary header and content remain reachable |
| Global workspace rail | 0 px | Removed; system Navigate menu and command palette preserve every destination |
| Compact header breakpoint | 1360 px | Hides secondary RADIO/CLUSTER chips; written state remains in workspaces and Stop remains visible |
| Canvas panel minimum | 240×150 px default | Panels remain focusable and recoverable; specialized panels may use stricter minima |
| Ordinary controls / rows | 36 / 34 px | Compact pointer/keyboard density |

Every major module is a `CanvasPanel`: drag by its written title bar, resize from any edge or corner, and click to raise it. Geometry is clamped to the visible workspace and saved in the safe `desktopLayouts` configuration section. Reset Workspace Layout clears only the active workspace geometry and restores authored defaults; Reset also exits Shack mode. Settings category navigation and detail are independent panels rather than fixed application chrome.

## Deterministic profiles

- Windows: 1366×768, 1920×1080, 2560×1440, and an effective 1280×720 logical viewport rendered at 150%.
- macOS: 1440×900, 1512×982, 1920×1080, 2560×1440, and an effective 1280×720 logical viewport rendered at 150%.
- Each profile captures 39 frames, including every destination and the existing TCI/panadapter/RF/Band Maps variants.

The scale soak test remains the non-visual resize/lifecycle gate. The hosted galleries are visual layout proof with deterministic private fixtures; they are not live-provider, audio, RF, hardware, or physical-display acceptance.
