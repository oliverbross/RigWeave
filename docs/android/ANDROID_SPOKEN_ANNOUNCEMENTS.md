# Android Spoken Announcements

Announcements use Android system TextToSpeech; no voice model is bundled. Frequency announcements are debounced after tuning settles. Optional events cover band/mode, allocation warnings, high SWR, addressed Digi messages, and critical route loss.

Speech is suppressed when disabled, unavailable, quiet profile is active, the radio is transmitting, or a voice macro is busy. Global Stop, background, and close cancel speech. TTS output has no path to radio TX audio.

Settings persist event choices, rate, and an optional installed system voice name.
