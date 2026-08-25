# Desktop Responsive Layouts

## Shell rules

| Rule | Value | Result |
|---|---:|---|
| Safe window minimum | 1180×720 logical | Primary header and content remain reachable |
| Expanded rail | 238 px (220–264 split bounds) | Icon, label, group and status badge |
| Collapsed rail | 64 px | Icon, tooltip, selected and focus state |
| Auto-collapse breakpoint | 1420 px | Preserves workspace width on 1366-class Windows displays and scaled screens |
| Compact header breakpoint | 1360 px | Hides secondary RADIO/CLUSTER chips; written state remains in workspaces and Stop remains visible |
| Content pane minimum | 900 px | Prevents a lost/zero-width workspace |
| Ordinary controls / rows | 36 / 34 px | Compact pointer/keyboard density |

The safe expanded preference is persisted under the display configuration. Narrow-window auto-collapse does not overwrite that preference. Reset Layout restores a visible expanded preference and exits Shack mode.

## Deterministic profiles

- Windows: 1366×768, 1920×1080, 2560×1440, and 1920×1080 at 150%.
- macOS: 1440×900, 1512×982, 1920×1080, 2560×1440, and 1920×1080 at 150%.
- Each profile captures 39 frames, including every destination and the existing TCI/panadapter/RF/Band Maps variants.

The scale soak test remains the non-visual resize/lifecycle gate. The hosted galleries are visual layout proof with deterministic private fixtures; they are not live-provider, audio, RF, hardware, or physical-display acceptance.
