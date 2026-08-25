# Desktop Flightline UI Convergence v1

This branch converges the Qt desktop shell with the proven Android Flightline information architecture while retaining desktop-native behavior and all pre-existing safety boundaries.

## Delivered

- A canonical 48-command C++ registry shared by navigation, platform menus, keyboard shortcuts and command palette.
- A packaged, original 40-icon SVG family plus native `.ico`/`.icns` application assets.
- Full-width workspaces with no persistent global side navigation.
- A native macOS global menu with Qt application roles and no in-window menu.
- An Alt-accessible Win32 File/Edit/View/Radio/Navigate/Tools/Window/Help menu attached to native chrome.
- Adaptive status header, persistent written Global Stop, accessible names/focus/tooltips and shortcut reference.
- Complete 39-frame deterministic destination gallery, including Presets, across narrow, standard, high-resolution and 150% profiles.
- New tablet atlas, baseline gap, workspace, reachability, menu, iconography, responsive and visual acceptance evidence.

## Architecture and safety

QML never dispatches a menu/navigation action directly to a hardware controller. It invokes a stable command ID; `DesktopApplication` applies allowlisted routing and then calls the existing owner. Unknown and disabled commands are inert. Radio restore remains disconnected/disarmed, Connect requires explicit profile/route selection, transmit authority is not added, and Global Stop remains the only cross-domain safety mutation.

Gallery mode remains opt-in and private. Its fake TCI endpoint binds loopback only, ignores transmit/tune messages, uses an ephemeral data root, and quits after capture. Normal startup never sees deterministic gallery data.

## Verification and evidence

See `docs/ui/` for the full reference and acceptance set. Hosted artifacts must match the final pushed SHA; any later source change invalidates exact-SHA acceptance until both workflows rerun. Unsigned/not-notarized macOS and unsigned installer proof are packaging evidence only; this programme does not publish or deploy them.
