# Desktop Hardware Acceptance

Source completion does not authorize a hardware claim. All profiles restore disconnected; PTT, TUNE, keyer send, Digi transmit, automatic Doppler and rotator movement remain unavailable until a named profile passes capability and readback acceptance on that platform.

| Boundary | Deterministic source evidence | Still required for physical acceptance |
|---|---|---|
| KX3/KX2 and QMX/QMX+ | Serial/TCP framing, bounded parsing, frequency/mode readback fixtures | Real device identity, frequency/mode readback and safe disconnect |
| FlexRadio | Linked native Rust adapter and generation-safe lifecycle | Real radio discovery/session/audio and explicit TX acceptance |
| RGO ONE V6 | Exact `ID006;` proof before setters; unknown firmware rejected | Real V6 identity/readback and supported-command capture |
| Hamlib radio | Pinned lifecycle and disabled restore | Real rig backend/capabilities/readback; separate CAT/PTT/TUNE evidence |
| Native/Hamlib rotator | Telemetry polling, limits, forbidden-path sampling and Stop fixtures | Real az/el feedback, Stop and explicit movement acceptance |
| Panadapter/Digi audio | Exact route/sample-rate ownership and route-loss stop | Real I/Q/RX audio routes and decode capture |
| Voice/keyer/EQ | Local preview/draft/review only | Audio route, CAT exclusivity, readback/rollback and transmit acceptance |

No protected tablet, radio or rotator was operated by this programme.
