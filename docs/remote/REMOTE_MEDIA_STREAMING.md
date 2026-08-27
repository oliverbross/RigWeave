# Remote Media Streaming

RX audio and derived spectrum use the binary frame documented in `REMOTE_MEDIA_CODEC_DECISION.md`. The station assigns monotonically increasing 32-bit sequences and the current 64-bit context generation. Android rejects stale generations, bad sizes, reserved bits, unsupported versions, and unauthenticated media.

Spectrum is a bounded projection of the existing Panadapter owner, at most 2048 quantized bins. Android labels it `REMOTE DERIVED SPECTRUM`; it is not raw I/Q evidence. RX audio uses PCM16 with explicit sample rate and feeds the existing audio owner. Queueing is single-worker and bounded by WebSocket/frame caps; disconnect drops pending media.

No audio, spectrum, waterfall, or I/Q is persisted, exported, placed in support bundles, or treated as proof of audible/physical reception. Opus and raw I/Q are not packaged in v1.

