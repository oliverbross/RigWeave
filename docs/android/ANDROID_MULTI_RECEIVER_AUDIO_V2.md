# Android Multi-Receiver Audio v2

`TciRxAudioController` is the sole TCI playback owner under the existing Android audio lease. It accepts two bounded receiver queues and renders one stereo float stream.

Modes are receiver A, receiver B, stereo split, and mix. Each receiver has gain, mute, solo, and pan; master level and crossfade are bounded and persisted. Sources are resampled only when rates differ, output is limited, and per-receiver overflow/underflow counters are reported.

Playback and both TCI audio streams require an explicit operator start. Background, disconnect, profile change, Global Stop, and close stop the route. Source/build evidence does not prove physical audio quality.
