# Remote TX and Interlocks

Remote PTT, Tune, and TX audio require OPERATOR role, foreground healthy session, writer lease, short TX lease, matching generation, station-side per-session arm, verified physical TX acceptance, the existing radio/TCI TX authority, and all existing interlocks/readback.

Any ambiguity, backgrounding, heartbeat expiry, disconnect, radio/profile switch, revocation, local pre-emption, Global Stop, or process restart clears authority. TX never persists and reconnect never resumes PTT.

In this candidate the desktop radio owner reports `pttAvailable=false` and `tuneAvailable=false`. Therefore station TX audio is parsed and bounded but rejected, TX/Tune mutations return `TX_OWNER_OR_PHYSICAL_ACCEPTANCE_UNAVAILABLE`, and third-party PTT is denied. This is a deliberate fail-closed result, not an incomplete RF claim.
