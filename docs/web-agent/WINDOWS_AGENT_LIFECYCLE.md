# Windows Agent lifecycle

- Per-user portable ZIP and NSIS installer; no administrator requirement for normal use.
- `RigWeave-AgentCtl.ps1` provides start, stop, restart, status, browser open, diagnostics, backup, restore preview/apply, update metadata verification and uninstall preview.
- Startup is opt-in; Windows Credential Manager/DPAPI remains the only persistent secret store.
- Application data, configuration, logs and support outputs live outside the install directory and survive ordinary uninstall/update.
- Serial/USB/TCP/audio discovery and Windows firewall changes require explicit operator action. No unnecessary external radio daemon is bundled.
