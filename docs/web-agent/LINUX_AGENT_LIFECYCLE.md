# Linux Agent lifecycle

- CPack TGZ and DEB candidates include the Agent and lifecycle controller.
- The supplied hardened `systemd --user` unit is disabled by default.
- Secrets use Secret Service/libsecret; when unavailable, only session-memory use is allowed and plaintext persistence is rejected.
- Serial/audio groups and narrow udev rules are operator-reviewed; wildcard device access is forbidden.
- Stop, diagnostics, backup/restore preview, update preview and safe removal preserve user data by default.
