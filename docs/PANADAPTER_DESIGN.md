# Panadapter and waterfall design

## Signal contract

RigWeave accepts real stereo I/Q PCM. Left is I and right is Q. A mono route is rejected on iPad because duplicating one channel would produce a misleading symmetric display. Spectrum reversal is an explicit operator setting for interfaces whose I/Q polarity is reversed.

The shared 1,024-point complex FFT performs:

1. independent I and Q mean removal;
2. a Blackman-Harris window;
3. radix-2 complex FFT and FFT shift;
4. magnitude normalization by the window's coherent gain;
5. dBFS conversion with a -140 dB floor;
6. fast attack and slower release smoothing in dB space.

Automatic per-frame Gram-Schmidt correction was removed. A live RF frame cannot distinguish front-end imbalance from legitimate asymmetric spectrum content, so deriving a correction from that same frame can suppress or rotate real signals. Any future I/Q calibration must be an explicit measured calibration with stable stored coefficients.

## Display model

- Spectrum: 41% of the instrument height.
- Frequency scale: a dedicated strip between spectrum and waterfall.
- Waterfall: 59% minus the scale strip; newest row first and scrolling downward.
- Scale: center frequency from live CAT and span from the physical audio sample rate.
- Floor: two-pass trimmed mean (bins at or below the first-pass mean), excluding the DC notch, followed by EMA smoothing.
- Color: operator-selected piecewise perceptual gradients; black-level offset and dynamic range remain independent controls.
- Empty state: no generated samples. The instrument remains explicitly offline until physical stereo I/Q arrives.

## Reference findings

The design uses concepts observed in the owner-authorized QMX Panadapter and AetherSDR sources without copying their platform implementations:

- QMX: 1,024-point complex FFT, Blackman-Harris window, about 30 FPS, distinct trace and waterfall smoothing, newest-at-top waterfall, configurable black level/contrast/floor blend, and realistic treatment of a narrow direct-conversion DC artifact.
- AetherSDR: roughly 40/60 spectrum/waterfall hierarchy, dBm-domain data, a trimmed live noise-floor estimate, independent dynamic-range controls, gradient color maps, frame coalescing, and accelerated compositing with a CPU fallback.
- Thetis: avoid preserving stale waterfall alignment across center-frequency changes; current releases deliberately sleep or realign waterfall data during frequency-frame transitions rather than smearing invalid history.

Reference repositories:

- `https://github.com/aethersdr/AetherSDR`
- `https://github.com/ramdor/Thetis`
- local owner checkout `/Users/oliver/Documents/M5Stack Core2/qmx-panadapter`
