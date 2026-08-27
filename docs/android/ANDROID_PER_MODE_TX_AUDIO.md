# Android Per-Mode TX Audio

Per-mode levels support CW, SSB, AM, FM, PSK31, RTTY, MFSK, SSTV, and a bounded default. Each mode either inherits the default or stores an explicit clamped override. Writes are debounced and versioned in preferences.

This is a calibration contract only: `sendEnabled` and physical acceptance remain false. ALC/SWR values may be displayed for a debug abort demonstration, but no production PTT, TUNE, TX-audio, drive, or RF action is exposed by this work.
