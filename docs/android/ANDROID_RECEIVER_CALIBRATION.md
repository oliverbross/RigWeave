# Android Receive Calibration

Receive calibration is keyed by source identity and stores level offset dB, frequency correction ppm, I/Q gain correction and I/Q phase correction. Values are bounded to ±100 dB, ±250 ppm, gain 0.5–1.5 and phase ±30 degrees.

The guided flow requires an operator-supplied known level/measured level before it records `CALIBRATED BY USER`. Until then the source remains `UNCALIBRATED` or `RELATIVE`, and measurements use dBFS. Calibration is never inferred from a demo trace, noise floor, S-meter, receiver model or historical survey.

Frequency and I/Q correction are applied to copied sample arrays before capture/analysis so the original source buffer is not mutated. Calibration is receive-only and cannot write radio settings, move a VFO or claim laboratory accuracy. Reset restores the truthful uncalibrated state.
