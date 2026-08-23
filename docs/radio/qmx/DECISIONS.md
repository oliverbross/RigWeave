# QMX/QMX+ core decisions

1. The core is Android-only and package-isolated. It defines typed ports; it does not edit or call central Radio, Panadapter, Digi, configuration recovery, health or native-build authorities.
2. USB identity reaching this package is already a stable digest. Raw serial numbers never enter diagnostics.
3. CAT uses one serialized queue. Safe continuous controls coalesce last-write-wins; queries alone may retry; edge-triggered and transmit commands never blindly retry.
4. Q9 and Q3 writes are not evidence. Write echoes are discarded, a delay separates them from the query, and readiness requires positive readback.
5. Unknown means unknown. Product strings distinguish QMX+ only when explicit; capability controls remain hidden until firmware, interface and readback evidence agree.
6. The only accepted audio route is the same hashed QMX device, 48 kHz stereo UAC. No built-in microphone or generic audio fallback exists.
7. The QMX nominal IF is +12 kHz. An override is diagnostics-only and bounded to the physical 48 kHz window.
8. I/Q orientation must be supplied/proven by the adapter. The operator swap setting inverts a known orientation; it does not manufacture proof from `UNKNOWN`.
9. Direct tone TX accepts only a complete immutable 79-symbol FT8 or 105-symbol FT4 plan from Digi. Wall time identifies the slot; monotonic deadlines drive cadence.
10. TX cleanup always attempts `TA0`, waits 5 ms, then `RX`. Any retry is requested from the central safety port, and failure to confirm RX latches a fail-closed result.
11. Menu terminal writes are impossible until the operator explicitly opens a positively detected extra CDC interface. The primary CAT interface is never borrowed.
12. Settings never persist TX active, Digi arm, tune active, terminal session or queued CAT commands.
