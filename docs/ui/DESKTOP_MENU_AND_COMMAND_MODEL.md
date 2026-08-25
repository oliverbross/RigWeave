# Desktop Menu and Command Model

`DesktopApplication::commands()` is the canonical registry. Its 48 stable records carry ID, label, category, SVG key, platform shortcut, optional destination, rail membership and enabled state. Rail delegates, Windows menu actions, macOS `QAction`s, application shortcuts and the command palette all call `Desktop.invokeCommand(id)`.

## Platform menus

macOS uses a native `QMenuBar` created in C++ and suppresses the QML in-window menu. Qt menu roles place About, Settings and Quit into the RigWeave app menu. File, Edit, View, Radio, Navigate, Window and Help use Command shortcuts and system window chrome. Services/Hide/Show All remain platform managed.

Windows uses the QML menu bar with `&File`, `&Edit`, `&View`, `&Radio`, `&Tools`, `&Window`, and `&Help`, preserving Alt access and Ctrl shortcuts. It is deliberately in-window and retains native window chrome.

## Enablement and safety

Commands without a completed service are visible where the programme requires discoverability but disabled; they cannot dispatch. Fast Entry and ADIF import/export route to the real Logbook dialogs. Export Configuration routes to Settings. Disconnect calls the radio controller. Global Stop calls the existing fail-closed stop chain. Connect remains disabled because a profile/route must be selected in the Radio workspace rather than guessed.

Escape, the visible header action, both platform menus, and the command palette resolve to `radio.stop`. Text editing keeps standard Undo/Redo/Cut/Copy/Paste/Select All actions through the current focus object.

`desktop_ui_contract_tests` proves stable unique IDs, 19 unique rail destinations, required safety/navigation commands, SVG coverage, platform shell markers and responsive breakpoints.
