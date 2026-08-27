# Android Scanner v2

Scanner v2 adds persisted banks, enabled memories, a persisted priority memory, filter/mode defaults, threshold, dwell, resume policy, explicit record preference, skip/lockout input, and a bounded derived activity journal.

Priority is checked every five visited memories. All tuning is receive-only; manual tune, background, disconnect, profile change, Global Stop, and close stop the active scanner, which never restores.

Record-on-hit is OFF/AUDIO/IQ. AUDIO truthfully reports unavailable until an owned recording source exists. IQ stores only a bounded reduced-display bookmark through the time-shift authority. Policy clamps pre-roll, post-roll, maximum duration, daily bytes, and total bytes; quota/source failures are shown rather than silently recorded.
