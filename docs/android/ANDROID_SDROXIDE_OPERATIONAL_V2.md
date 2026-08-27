# Android SDRoxide Operational Enhancements v2

Status: source-complete candidate on `feature/android-sdroxide-enhancements-v2`.

This increment turns the existing receive-only Android SDR surface into an operator workbench: targeted TCI mutations with readback, receiver linking, a two-receiver audio mixer, Panadapter v5 review controls, bounded time-shift/bookmarks, bounded PSK31/RTTY skimmers, scan banks/priority/journal/capture policy, and per-mode TX-audio calibration state.

Safety remains unchanged. TCI exposes no PTT, TUNE, TX-audio, or automatic transmit action. Per-mode TX levels are configuration only and real send is locked pending separate physical acceptance. Active scanning, audio, skimmers, time-shift playback, and pending writes do not restore.

Protocol audit verdicts: TCI spot exchange is `UNAVAILABLE_PROTOCOL`; coherent diversity is `UNAVAILABLE_PROTOCOL`. Neither is simulated or advertised as live.

Debug Lab is deterministic fake hardware labelled `DEMO · NO RADIO`. It proves UI, routing, bounded processing, lifecycle, and error-state behavior only.

## Candidate evidence ledger

1. Frozen v1 base resolves to `bcf860b208cba9a81378a82dad54f8c83adb6b0f`.
2. Protected local `main` remains `27c70d0c2ab0ae21ef18d7fd9b39f8878b0940ea`.
3. Frozen `origin/main` remains `fb04d52df0c9ccc305125449bb188ef8e3f0185e`.
4. The v2 worktree was created from the exact clean v1 base.
5. SDRoxide v1.5.3 remains the latest stable audited release.
6. The audited commit is `a680935b10f33768a499435e8bd37f779fa640ae`.
7. The audited tree is `4697195080495da4a727b14234b85af89c10ecda`.
8. The audited GPL-3.0 licence digest is recorded in provenance.
9. No SDRoxide source or asset is incorporated.
10. Stable TCI VFO control is shared through the native codec.
11. Stable TCI IF control is shared through the native codec.
12. Stable TCI split and global-volume control is shared through the native codec.
13. Receiver-targeted safe mutations are generation scoped.
14. Rapid safe setters use latest-write-wins coalescing.
15. Pending writes are cleared on disconnect and never replayed.
16. Readback confirmations and two-second timeouts are surfaced.
17. Spot exchange is `UNAVAILABLE_PROTOCOL`.
18. Diversity is `UNAVAILABLE_PROTOCOL`.
19. TCI PTT, TUNE, TX audio, and automatic transmit remain blocked.
20. Receiver links are explicit and bounded to one peer.
21. One TCI audio owner controls one stereo Android route.
22. Receiver A/B, stereo split, and mix modes are implemented.
23. Per-receiver gain, mute, solo, and pan are bounded and persisted.
24. Two audio input queues are bounded to eight frames each.
25. Audio resampling occurs only for mismatched rates.
26. Audio overflow/underflow and limiter state are visible.
27. Digit entry and decade stepping clamp receive frequencies.
28. Time-shift is bounded to OFF/30/60/120 seconds.
29. Paused/scrubbed/replayed traces are labelled separately from live.
30. Receiver/source changes invalidate incompatible time-shift state.
31. Bookmarks store reduced traces only and can be deleted.
32. Bookmark retention is capped at 256 rows.
33. PSK31 and RTTY skimmers are explicit opt-in.
34. Candidate extraction is capped at four markers per mode.
35. Candidate energy is not labelled as a confirmed decode.
36. Marker review and Digi navigation never auto-tune or transmit.
37. Scan banks persist memories, filters, thresholds, dwell, resume, and priority.
38. Priority watch runs at a bounded five-memory interval.
39. Scanner activity uses a separate bounded derived journal.
40. Record-on-hit is explicit; unavailable audio capture is reported.
41. IQ record-on-hit stores a reduced display bookmark through one capture owner.
42. Capture duration, daily bytes, and total bytes fail closed at bounded limits.
43. Per-mode TX levels implement inheritance, override, clamp, and debounced persistence.
44. Production TX-level send remains locked without physical acceptance.
45. Android JVM validation passed 728/728 tests; instrumentation sources and test APK compile/package passed.
46. Native normal, ASan, and UBSan suites passed 5/5 each; Flex passed 98 with one ignore, Tempo 160, and MFSK 407 with 28 ignores.
47. Unsigned generic iOS simulator/device builds and macOS desktop 10/10 CTests passed; Windows remains hosted-only.
48. Exact-SHA hosted validation and protected-tablet/device evidence are recorded only after those separate gates complete.
