# Android TCI Physical Acceptance

This checklist is the only production acceptance upgrade path. Ordinary settings, config import/restore, reconnect, app update, and Debug Lab cannot self-certify.

For each exact profile endpoint and reported device identity, advance one state at a time. Record operator confirmation, clean RX before/after, sanitized device identity, timestamp, protocol/firmware, commands/readback, latency, bounded limits, and outcome. The evidence must be non-demo and RX-confirmed.

1. Confirm receive-only connection and identity; advance to `READ_ONLY_ACCEPTED`.
2. Exercise only safe setters with exact readback; advance to `SAFE_SETTERS_ACCEPTED`.
3. Use a protected loopback/dummy path with no RF; verify framing, level, monitor, and recovery; advance to `TX_AUDIO_LOOPBACK_ACCEPTED`.
4. With a safe load and explicit operator control, verify momentary PTT/readback/de-key; advance to `PTT_ACCEPTED`.
5. Verify capped finite Tune and RX recovery; advance to `TUNE_ACCEPTED`.
6. Under the authorized RF procedure, verify RF behavior and interlocks; advance to `RF_ACCEPTED`.

Stop on identity drift, ambiguous readback, unexpected RF, unsafe SWR/ALC, route loss, or failed RX recovery. A new identity resets acceptance. Debug/fake evidence is never transferable.
