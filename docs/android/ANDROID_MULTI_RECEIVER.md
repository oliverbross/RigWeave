# Android Multi-Receiver

The runtime models up to eight server-declared receivers and renders the first two simultaneously. Each receiver carries VFO A/B, selected channel, effective RX frequency, mode, passband, mute, enable, I/Q, audio, sample rate, drop count, and source age.

Control receiver and listening receiver are separate explicit choices. One Panadapter controller owns at most two native contexts; each context is keyed by receiver and destroyed on detach, profile change, disconnect, background cleanup, or close.

No receiver state is restored as connected or streaming after process restart.
