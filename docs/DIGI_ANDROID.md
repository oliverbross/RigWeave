# Android Digi integration

## Delivered engine

Android Digi uses component-audited source from Nexus commit
`6ec4a7925f1550cc364c7fd95967ce38c696ad3f`.

- CW: deterministic waveform synthesis plus adaptive streaming audio decode.
- RTTY: ITA2/USOS character handling, shaped 45.45-baud AFSK generation,
  streaming fldigi-derived demodulation, squelch, confidence, and AFC.
- SSTV: streaming VIS detection and progressive image decode; TX/RX for PD-50,
  PD-90, PD-120, PD-160, PD-180, PD-240, PD-290, Robot 24/36/72,
  Scottie 1/2/DX, and Martin 1/2.

The complete `tempo-sstv` MIT licence and NOTICE are retained under
`rust/tempo-sstv`. CW/RTTY provenance is recorded in
`rust/rigweave-flex/UPSTREAM.md`.

## Android operating path

KX2/KX3 receive uses the selected USB audio input, disables Android AGC,
noise suppression and echo cancellation, then resamples to the modem's
12 kHz clock. Transmit acquires the selected DigiRig output before any CAT
action, sets Elecraft DATA mode, verifies fresh `TQ` after `TX;`, streams
the generated waveform, sends `RX;`, and verifies RX. Failure is fail-closed
or visibly RX-unconfirmed.

Flex receive taps decoded VITA float or Opus PCM before playback gain/mute,
downmixes it to mono, and feeds the same 12 kHz modem clock without using an
Android microphone. Flex transmit uses the existing session transmit gate,
interlock, remote audio stream, Opus packetizer, MOX lifecycle, and bounded
cleanup. Both paths are built and protocol-tested but have no physical Flex
hardware acceptance in this delivery.

Mode, CW pitch/speed, RTTY sense, SSTV mode, and TX text persist in app-private
preferences. Arming and selected image pixels deliberately do not persist.

## Not exposed

FT8, FT4, FT2, Q65, FST4/FST4W, MSK144, JT65, and WSPR are not exposed.
Nexus routes these through `tempo-fast-sys`, which builds a large vendored
WSJT-X/Decodium Fortran, C, and C++ modem with CMake, FFTW, and desktop-specific
build branches. Nexus has no Android target. The examined Decodium C++ migration
still carries Qt/FFTW requirements and unresolved Fortran-compatible symbols.
Showing those modes before an Android engine passes upstream parity fixtures
would be a placeholder and would violate RigWeave's product contract.

## Nexus feature audit

RigWeave already has the stronger POTA/WWFF chase, recoverable POTA activation,
offline SOTA catalogue, local journal, and authority-aware QRZ/Club Log/eQSL
delivery paths. They were not replaced. Nexus LoTW upload shells out to desktop
TQSL for certificate signing, which is not an Android implementation; only its
LoTW report download is portable. Nexus Field Day has useful 2026 scoring and
exchange domain logic, but it is a separate event workspace and was not mixed
into Digi without its durable QSO/event model and tests.
