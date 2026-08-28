# Remote Station Administration Guide

Desktop Settings → Remote Station exposes service enable, station identity/name, listen address/port, TCI/rigctld switches, sessions, paired-device count, a bounded bridge-writer arm, and Global Stop. Health exposes sanitized protocol/session/rejection/media metadata.

Safe sequence:

1. Keep loopback and read-only defaults while configuring.
2. Start; record the certificate fingerprint out-of-band.
3. Generate an offer and approve the exact pending device/role locally.
4. Revoke lost devices immediately.
5. Arm bridge writing only for the required 30-second interaction.
6. Use Global Stop on any ambiguity; reconnect requires fresh lease actions.

Do not place private keys, session ids, exact private addresses, raw media, QSO/provider content, or voice assets in support records. Config export contains public/alias metadata only and restores service OFF, remote disconnected, and all arms cleared.
