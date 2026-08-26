# Edit Layout Mode

Edit Layout is a secondary, explicit state available from View, the command
palette, or Cmd/Ctrl+Shift+L.

## Normal state

- authored panels are locked;
- no move/resize cursor, handle, reset button, grid, or z-order menu is shown;
- keyboard focus and control activation remain stable;
- saved custom geometry, when present, is locked;
- Reset Workspace Layout restores the official layout.

## Editing state

- a persistent amber banner says `EDIT LAYOUT`;
- Done Editing and Reset Official Layout are always visible;
- an 8 px grid and resize handles are visible;
- move and resize snap to the grid;
- panel minimum size and workspace bounds are enforced;
- an edit cannot create a panel overlap;
- right-click offers Restore official panel position, Bring Forward, and Send Back;
- Escape invokes Global Stop, exits Shack/overlays, and locks layout editing.

Geometry is persisted proportionally under `desktopLayouts` with
`layoutVersion = 2`. Display removal, scale change, window reduction,
non-finite/corrupt values, and future versions clamp or fall back safely.
Changing destination exits Edit Layout. Layout state never restores radio,
audio, transmit, provider, or rotator authority.

