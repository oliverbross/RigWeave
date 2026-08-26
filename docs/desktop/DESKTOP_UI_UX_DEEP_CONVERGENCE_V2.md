# Desktop UI/UX Deep Convergence v2

Branch: `feature/desktop-ui-ux-deep-convergence-v2`

Accepted source: `9c0e3d6f70013e95b39107f782dee0cb4b463e7c`

## Architecture

- The grouped Flightline sidebar is the primary discoverable navigation.
  Native Navigate menus, shortcuts and the command palette use the same
  registry.
- Every workspace opens in a locked authored layout. Free movement is removed
  from normal operation.
- Edit Layout is a checked canonical command with a banner, 8 px grid,
  proportional versioned persistence, overlap prevention, bounds/minimums,
  panel context actions, Done, Reset, and Escape dismissal.
- Home, Radio, Digi, EQ and Panadapter have screen-specific tablet-derived
  compositions. Existing Logbook, Settings, Health, About and connected
  foundations retain their working owners inside predictable official layouts.
- The header carries workspace, station, grid, UTC, observed frequency/mode,
  written radio/cluster state and Global Stop; build/version clutter is absent.

## Safety and truth

No Android source, service owner, protocol, database, mode, provider, radio
command or credential path was added. Radio restore remains disconnected,
TCI receive-only, PTT/TUNE unavailable, Digi TX unarmed, EQ apply gated,
rotator disarmed, Groups drafts unsent, and all handoffs explicit.

## Evidence boundary

Deterministic galleries prove source reachability and rendering only. They are
not authenticated-provider, live audio, CAT/PTT/TUNE, RF, physical radio,
rotator movement, physical Windows, signing, notarisation, release, or
deployment evidence.

The local execution and reviewer-disposition record is
`docs/ui/DESKTOP_UI_UX_V2_VALIDATION_EVIDENCE.md`. Exact-SHA hosted Windows
results remain pending and are not inferred from macOS evidence.
