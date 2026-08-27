# Android TCI TX Interlocks

Preflight requires exact profile/device identity, sufficient acceptance, foreground/context validity, connected readback, confirmed non-transmitting/non-tuning state, finite bounded audio, and stable mode/frequency. PTT must be read back before the first TX-audio frame.

Active monitoring covers connection, PTT truth, mode/frequency drift, identity/context/route, SWR, reflected power only when genuinely available, ALC only when genuinely available, audio send/finite state, queue accounting, watchdog, and Global Stop. Any violation stops audio, requests both `trx:false` and `tune:false`, then waits for RX truth. There are no blind retries.

Tune is separate from PTT. It requires `TUNE_ACCEPTED`, uses the capped tune drive, has a hard duration watchdog, and follows the same stop and RX recovery. An uncertain recovery latches `RX_UNCONFIRMED`; Digi automation, Keyer, Voice, PTT, and Tune remain disabled until a fresh RX recheck succeeds.
