# Remote Media Codec Decision

Decision: use bounded PCM16 for RX audio and quantized derived spectrum frames for the LAN/VPN-first v1 candidate. Do not package Opus yet.

Reasons:

- keeps provenance/package delta zero;
- deterministic decoding on Qt and Android;
- explicit sample rate and sequence/generation metadata;
- adequate for trusted low-latency LAN/VPN evaluation;
- avoids claiming codec/jitter quality before live acceptance.

Frame header: `RWR1`, protocol version, typed channel, flags, 32-bit sequence, 64-bit timestamp, 64-bit generation, 32-bit payload size. Maximum payload is 256 KiB. Control JSON is capped at 64 KiB. RX PCM payload begins with a big-endian sample rate and contains little-endian signed 16-bit samples. Spectrum is a derived 0–255 trace capped at 2048 bins and is labelled as derived, never raw I/Q.

Raw I/Q, Opus, adaptive bitrate, station-side resampling, and persistence are future reviewed options. No media buffer is exported or restored.

