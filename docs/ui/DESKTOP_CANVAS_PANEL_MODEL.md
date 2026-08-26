# Desktop Canvas Panel Model

Windows and macOS treat the workspace client area as a freeform canvas. This is intentionally different from the fixed, touch-first tablet composition.

## Interaction contract

- Every major functional module is a `CanvasPanel` with a written title.
- Dragging the title bar moves the panel and raises it above overlapping peers.
- Every edge and corner is a resize target; the pointer cursor communicates direction.
- Geometry is constrained to the visible workspace and each panel has a usable minimum size.
- Each title bar exposes a labelled per-panel restore action.
- View → Reset Workspace Layout clears only the current workspace geometry and restores all authored defaults.
- Global Stop, explicit-connect rules, capability gates and service ownership are unchanged by layout manipulation.

## Persistence and recovery

Logical-pixel `x`, `y`, `width` and `height` values are debounced and stored under the safe `desktopLayouts` configuration section. Values must be finite and are bounded to 0–8192 before persistence. Credentials, live radio state, PTT/TUNE state, rotator arm/movement state and provider bodies never enter panel geometry.

Panels retain a separate intended rectangle while the application window is starting. Temporary clamps against a zero-size or partially constructed canvas are never promoted to the saved layout; the intended rectangle is reapplied as the real canvas dimensions arrive. Direct user movement and resizing update that intended rectangle, then persist it on release.

Panels clamp themselves after a settled window resize so a title bar cannot become unreachable. Reset deletes the active workspace map, emits a targeted reset signal and leaves every other workspace layout intact.

## Evidence boundary

Contract tests cover the reusable components, all routed pages, persistence APIs, intended-geometry restoration and the native reset command. Local UI stress cycles workspace loading, Settings categories, panel movement/resizing, full screen and window resizing. A physical macOS pointing-device pass additionally proved drag, edge/corner resize, z-raise, navigation persistence, quit/relaunch persistence and native-menu reset in an isolated private QA profile. Gallery images demonstrate authored defaults; Windows pointing-device behavior, radio/audio hardware, RF and deployment remain separate acceptance layers.
