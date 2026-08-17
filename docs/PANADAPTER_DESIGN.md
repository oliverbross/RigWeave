# Panadapter and waterfall design

**Status:** Current implementation description. Phase 0 changed documentation only.

## Signal contract

RigWeave consumes real stereo I/Q PCM. Left is I and right is Q. Mono input must not be duplicated into a misleading symmetric display. Spectrum reversal is an explicit operator setting for interfaces whose I/Q polarity is reversed.

The shared implementation is in core/portable/include/kx3/panadapter_dsp.hpp and core/portable/src/panadapter_dsp.cpp, exposed through core/include/rigweave/core.h. It performs DC removal, windowing, complex FFT/magnitude processing, smoothing, bin copying, and I/Q metrics.

Apple feeds the core from AVAudioSession in ios/RigWeave/FeatureModel.swift and renders the spectrum/waterfall in ios/RigWeave/ContentView.swift. Android binds the shared/native path through its JNI build and uses AudioMonitorController/MainActivity for physical input, monitoring, and presentation.

## Display contract

- Spectrum/waterfall is instrumentation, not generated decoration.
- Centre frequency comes from live CAT; span comes from the physical sample rate.
- Newest waterfall history stays aligned with the current frequency frame.
- Noise floor, black level, dynamic range, palette, and I/Q reversal are explicit.
- No physical input means an offline/empty instrument.

## Evidence

- Shared host build and focused CTest passed during Phase 0.
- Generic iOS build and Android unit/assemble validation passed during Phase 0.
- Historical repository evidence records a physical iPad stereo-I/Q pass at 48 kHz and more than 1.2 million frames. Phase 0 did not repeat this device test.
- Android source and build paths exist, but a physical Android I/Q acceptance pass remains unverified.

## Residual gates

- Measure axis/calibration accuracy against known RF/audio sources.
- Reconfirm I/Q orientation, image rejection, frequency alignment, and device persistence on each supported interface.
- Separate capture proof, DSP proof, rendered-axis proof, and monitoring/playback proof.
- Phase 1A must harden this implementation rather than rewrite it.

Historical research references and owner-local paths are retained in [PORTING_NOTES.md](PORTING_NOTES.md); they are provenance clues, not imported Phase 0 code.
