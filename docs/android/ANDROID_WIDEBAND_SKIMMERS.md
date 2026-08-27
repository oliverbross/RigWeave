# Android Wideband Skimmers

PSK31 and RTTY lanes are explicit opt-in and restore stopped. Candidate extraction is limited to documented calling segments and at most four candidates per mode. Work is serialized off the main thread, stale markers expire, and decode duration is visible.

Existing native PSK31/RTTY receivers may promote text and a call-like token; FFT energy alone remains `CANDIDATE ONLY`. No marker tunes automatically and no skimmer action can transmit. Debug markers are labelled fake evidence.
