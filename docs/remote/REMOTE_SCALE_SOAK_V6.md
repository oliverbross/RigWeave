# Remote Scale and Soak v6

The deterministic host profile advances timestamps without sleeping and verifies bounded state over the equivalent of 30 minutes:

- 8 concurrent authenticated sessions, one writer and seven denied competitors;
- 36,000 waterfall frames at 20 fps;
- 90,000 PCM20 audio frames at 50 fps;
- 1,800 station-side Digi state frames at 1 fps;
- 10,000 control/media frames plus 10,000 deliberately corrupted frames;
- 1,000 session reconnect, writer, TX, rotator, Global Stop and close cycles;
- the first 100 reconnect generations also cover 100 context/radio-switch invalidations;
- network/background loss and stale generation clear TX in the protocol contract suite.

The test records encoded byte totals and requires zero decode drops for valid input, rejection of every corrupted input, an empty session set after shutdown, and no retained lease. ASan and UBSan run the same profile. It uses bounded stack/vector storage and no persistent media queue; process RSS/thread/FD monitoring remains a hosted/live-process evidence item rather than a fabricated unit-test number.

This deterministic profile is not proof of real network RTT, audio latency/jitter, audible output, third-party application behavior, or RF/rotator operation.
