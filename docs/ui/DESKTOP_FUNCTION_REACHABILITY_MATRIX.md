# Desktop Function Reachability Matrix

| Visible action | UI → command | Service / storage / result | Error and test | Status |
|---|---|---|---|---|
| Open any workspace | Rail/menu/palette → `nav.*` | `Desktop.setCurrentDestination` → safe display config → loader | Unknown IDs rejected; command/UI contract test | FUNCTIONAL |
| Expand/collapse rail | Rail/View → `view.sidebarMode` | `setSidebarExpanded` → display config | Narrow auto-collapse preserves preference; source contract | FUNCTIONAL |
| Show/hide rail | View → `view.sidebarToggle` | Window-local presentation state | Reset Layout recovery; gallery profiles | FUNCTIONAL |
| Command palette | Menu/shortcut → `tools.palette` | Opens searchable enabled registry | Disabled commands omitted; command contract | FUNCTIONAL |
| Global Stop | Header/menu/palette/Escape → `radio.stop` | Desktop stop → Radio/Rotator/Parity stop owners → safe state | Existing safety tests plus command contract | FUNCTIONAL |
| Disconnect radio | Radio menu/page → `radio.disconnect` | Radio controller disconnect | Idempotent disconnected result; platform safety tests | FUNCTIONAL |
| Connect radio | Disabled menu discoverability; Radio page controls | Selected backend/profile/route → Radio controller | Requires real selection/capability/readback | LIVE_ACCEPTANCE_PENDING |
| Receive review | Radio menu/page → `radio.review` | Navigate Radio; review remains receive-only | No CAT without explicit connected service | READ_ONLY_FUNCTIONAL |
| Fast Entry | File/shortcut → `file.fastEntry` | Logbook dialog → `Desktop.saveFastEntry` → SQLite | Form validation/database error; data contract tests | FUNCTIONAL |
| Import ADIF | File → `file.importAdif` | File dialog → Adif service → QSO database | Bounded parser/progress/cancel; data tests | FUNCTIONAL |
| Export ADIF | File → `file.exportAdif` | File dialog → Adif service → selected file | I/O error/progress/cancel; data tests | FUNCTIONAL |
| Import configuration | Disabled File action | Preview exists; mutation intentionally not dispatched | Disabled until reviewed import owner is complete | BLOCKED |
| Export configuration | File/Settings → `file.exportConfig` | DesktopConfig safe bundle → selected file | Secret/QSO/live-state exclusions; platform tests | FUNCTIONAL |
| Close/Quit | File/app menu → `file.close` / `app.quit` | Window close → Desktop shutdown | Idempotent shutdown/soak tests | FUNCTIONAL |
| Edit actions | Edit menu/shortcut → `edit.*` | Current focus object standard edit methods | No focus means no mutation; QML tests | FUNCTIONAL |
| Full screen | View/Window → `view.fullScreen` | Native window visibility | Same action restores windowed; visual profiles | FUNCTIONAL |
| Shack Display | View/Window → `view.shack` | Window Shack loader | Global Stop stays visible; gallery frame | FUNCTIONAL |
| Reset Layout | View → `view.resetLayout` | Restores visible rail/expanded preference/exits Shack | Prevents lost panes; source contract | FUNCTIONAL |
| Keyboard shortcuts | Shortcut guide / canonical registry | Application-scoped action dispatch | Disabled entries cannot fire; command contract | FUNCTIONAL |
| Provider enable/refresh | Settings provider rows | Parity provider lifecycle/cache | CURRENT/STALE/OFFLINE_CACHE/EMPTY/ERROR/UNAVAILABLE; parity tests | TRUTHFUL_FOUNDATION |
| Apply RX | Radio page | Radio requestFrequency/requestMode | Enabled only when connected; radio safety tests | LIVE_ACCEPTANCE_PENDING |
| Receiver control/listen | Radio receiver list | Explicit receiver selection | Receiver must exist; TCI contract tests | FUNCTIONAL |
| Panadapter controls | Panadapter workspace | DesktopPanadapter configuration/scene | Invalid ranges rejected; scale/TCI tests | FUNCTIONAL |
| Select RF evidence | Intelligence map/list | RfObservationModel shared selection | Empty/unavailable states; RF tests | READ_ONLY_FUNCTIONAL |
| Contest merge review | Contest workspace | Parity review state | Confirmation required; parity tests | READ_ONLY_FUNCTIONAL |
| Groups draft review | Groups workspace | Parity draft state | Nothing posts; parity tests | READ_ONLY_FUNCTIONAL |
| Satellite pass selection | Operations workspace | Parity active review | No Doppler/TX; parity tests | READ_ONLY_FUNCTIONAL |
| Rotator target/move | Rotator workspace | Rotator controller | Disarmed/capability gated; no motion acceptance | LIVE_ACCEPTANCE_PENDING |
| Support bundle | Disabled Tools/Help action | Sanitized support owner exists | Disabled until UI chooser/result lifecycle is complete | BLOCKED |
| Licences | Help → `help.licences` | About workspace + packaged notices | Package resource checks | FUNCTIONAL |

No visible enabled action bypasses the canonical command or its existing service owner. Disabled discovery items are never described as functional.
