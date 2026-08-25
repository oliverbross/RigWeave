# Desktop Responsive Layouts

## Shell rules

| Rule | Value | Result |
|---|---:|---|
| Safe window minimum | 1180×720 logical | Primary header and content remain reachable |
| Global workspace rail | 0 px | Removed; system Navigate menu and command palette preserve every destination |
| Compact header breakpoint | 1360 px | Hides secondary RADIO/CLUSTER chips; written state remains in workspaces and Stop remains visible |
| Content pane minimum | 900 px | Prevents a lost/zero-width workspace |
| Ordinary controls / rows | 36 / 34 px | Compact pointer/keyboard density |

Reset Layout exits Shack mode and restores the standard full-width workspace. Settings retains its own task-specific category sidebar because it is content navigation rather than global application chrome.

## Deterministic profiles

- Windows: 1366×768, 1920×1080, 2560×1440, and an effective 1280×720 logical viewport rendered at 150%.
- macOS: 1440×900, 1512×982, 1920×1080, 2560×1440, and an effective 1280×720 logical viewport rendered at 150%.
- Each profile captures 39 frames, including every destination and the existing TCI/panadapter/RF/Band Maps variants.

The scale soak test remains the non-visual resize/lifecycle gate. The hosted galleries are visual layout proof with deterministic private fixtures; they are not live-provider, audio, RF, hardware, or physical-display acceptance.
