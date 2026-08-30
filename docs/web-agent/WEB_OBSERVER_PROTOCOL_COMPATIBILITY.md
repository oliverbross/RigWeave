# Web observer protocol compatibility

Agent protocol `1.0`, observer protocol `1.0`, and media protocol `1.0` are the M2 baseline. The Agent sends HELLO first; the Hub validates the pinned TLS leaf, signs the exact challenge, and requests OBSERVER. Unsupported majors fail closed.

Remote control messages are bounded to 64 KiB, native media to 256 KiB plus header, eight sessions, five-second heartbeats, and 15-second expiry. Web fixtures remain canonical in RigWeave-Web; exact-SHA compatibility CI checks the frozen copies without a personal PAT.
