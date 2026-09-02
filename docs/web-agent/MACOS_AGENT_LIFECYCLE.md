# macOS Agent lifecycle

- Unsigned app/CLI candidate with a signing/notarization-ready bundle structure.
- `rigweave-agentctl` provides finite lifecycle, diagnostics, backup/restore and update-preview actions.
- The supplied LaunchAgent has `RunAtLoad=false` and `KeepAlive=false`; installation/enabling is explicit.
- Secrets remain in Keychain. USB/serial/audio permissions are requested by macOS in context.
- Removal deletes the app and optional LaunchAgent only; application data and Keychain items remain unless separately and explicitly removed.
