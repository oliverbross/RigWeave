# Android FM, Tone and RDS

NFM uses a bounded quadrature discriminator and 50/75 microsecond de-emphasis setting. CTCSS scans the standard amateur tone set with Goertzel energy, adjacent-tone comparison, minimum window and confidence threshold. DCS samples the 134.4 bit/s subaudible stream, checks normal and inverted 23-bit Golay words, requires repeated confidence and clears weak results. Neither path can generate or transmit a tone.

WFM is unavailable below 192 kHz usable source rate. The receiver measures the 19 kHz pilot, blends stereo rather than snapping, reports separation, and falls back to mono. The RDS path derives 57 kHz timing from the pilot, performs differential bit recovery, validates checkwords/offset syndromes and assembles bounded groups. PI, PS, PTY, TP/TA, RadioText, AF and valid clock metadata are reset on generation/station changes; invalid groups increase the visible block-error rate and cannot publish final text. No network station lookup occurs.

Debug fixture metadata is visibly `DEMO · NO RADIO` and is not evidence of a received station.
