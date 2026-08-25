# TCI Physical Acceptance

Software acceptance uses deterministic fake-server, renderer, scale, lifecycle, hosted build, gallery, and package evidence. It does not establish physical TCI dialect compatibility or RF behavior.

| Layer | Software evidence | Physical status |
|---|---|---|
| ExpertSDR/compatible TCI connection | WebSocket handshake, timeout, ready, TLS-fail-closed, reconnect tests | Pending |
| One receiver | Discovery, attach, I/Q routing, status readback tests | Pending |
| Multiple receivers | Two rows, explicit roles, concurrent attachments and isolated DSP contexts | Pending |
| I/Q sample-rate negotiation | 48/96/192 kHz tests; configured upper bound 10 MHz | Pending for each device/rate |
| Frequency/mode two-way sync | Command coalescing and readback adoption tests | Pending |
| Reconnect/adopt-current-state | Ambiguous write is not replayed; fresh status is authoritative | Pending |
| RX audio | Binary decode and route state exist | Pending for route, underflow/overflow, audible output |
| Filter capability/passband | View-only overlay; no unsupported command guessed | Pending |
| safe Stop/de-key | One-shot fake-server trx:false;tune:false, mutation cancellation, stream detach | Pending |
| PTT/TUNE/TX audio | No production TX-on path in this programme | Deferred and separately authorization-gated |

For physical acceptance, record device/model/firmware, endpoint/TLS mode, receiver count, negotiated rates, exact command/status transcript with secrets removed, readback behavior, reconnect behavior, RX-audio route, Stop latency, and all failures. Never infer a capability from an SDRoxide fixture or another vendor. Unknown remains unknown.
