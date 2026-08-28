# stationd on Windows, macOS, and Linux

`rigweave-stationd` shares the desktop configuration, credential vault, radio owner, rotator owner, and Panadapter owner. It is disabled by default.

Useful commands:

```text
rigweave-stationd --status
rigweave-stationd --list-clients
rigweave-stationd --revoke <device-id>
rigweave-stationd --foreground
rigweave-stationd --foreground --pairing-offer
rigweave-stationd --stop
```

Run it as an ordinary user with access to the same platform vault/configuration. Do not run a second copy on the same ports. Loopback is the default; LAN/VPN bind requires explicit configuration and still uses TLS 1.3. The service performs Global Stop on shutdown. Packages install only the executable/runtime dependencies—never keys, generated identities, demo media, or a rigctld binary.

Windows service registration, macOS LaunchAgent, and Linux systemd integration are operator deployment choices and are not performed by this branch.
