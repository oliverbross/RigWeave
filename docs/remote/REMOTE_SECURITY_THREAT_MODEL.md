# Remote Station Security Threat Model

Trust boundaries: untrusted LAN/VPN, station TLS endpoint, Android client, station platform vault, paired public-key metadata, existing radio/rotator owners.

| Threat | Control |
|---|---|
| Unauthorised client | TLS endpoint, paired P-256 public key, signed challenge |
| Stolen QR/pairing text | random nonce, two-minute expiry, single use, local approval |
| Certificate substitution | exact SHA-256 leaf pin; no trust-all option |
| Replay/session hijack | nonce challenge, session id, station generation, heartbeat expiry |
| Role escalation | station-approved stored role; requested role is not self-authorising |
| Writer/TX lease race | one holder per type, bounded TTL, generation checks |
| Network loss during TX | heartbeat expiry, foreground loss clearing TX, Global Stop |
| Malformed media/control | strict magic/version/reserved/size/channel checks and caps |
| Resource exhaustion | eight sessions, 64 KiB control, 256 KiB media, bounded socket/read buffers |
| LAN exposure | loopback default; LAN requires explicit setting; TLS remains mandatory |
| Revoked device | authority removal, live-session close, lease clearing |
| Local pre-emption | local action clears every remote lease and advances generation |

Residual risks: a compromised already-unlocked station process or Android process can use authority already available to that process; VPN security is external; first pairing offer must be transferred over an operator-trusted visual/out-of-band path. Remote TX is unavailable in this source candidate because the desktop owner has no verified PTT/Tune capability.

Private keys remain in Android Keystore or the desktop platform credential vault. Config/export stores public certificates, public keys, roles, aliases, and bounded metrics only. There is no remote command shell or custom encryption.

