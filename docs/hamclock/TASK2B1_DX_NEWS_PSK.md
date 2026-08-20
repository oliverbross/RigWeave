# Task 2B1 DX News and bidirectional PSK closure

- Start SHA: `8bd3dae85ed819646221a425ec7f230ee3774c36`
- Final implementation SHA: `3eec2ec`; the final pushed documentation tip is reported by the post-commit equality check because a commit cannot contain its own SHA.
- Branch: `feature/openhamclock-parity-v1`; pushed-tip equality is verified after this record is committed.
- Watcher: `python3 scripts/check_openhamclock_upstream.py` exited 0 with the stable pin unchanged at `d4a50eaaa61d3432a1de5f80cbe61790739930a5`.

## Gate 0

Cluster transport now has typed disabled/connecting/connected/retry/error state independent of solar errors; connected with zero current spots is empty, not error. HOME refresh cancels/excludes the legacy satellite ticker. Requested PSK items and requested DX pages are one-shot. NOAA HTTP is injected, HTTPS-only, status/type/size bounded, and the current-month SSN value is accepted. The shared in-flight coalescer removes a completed key before exposing completion.

## DX News

DX-World RSS is the direct news source. The existing shared NG3K ADXO feed supplies schedule context without a second request. DXNews.com is explicitly `UNAVAILABLE · no stable direct structured contract`; QO-100 was not introduced.

The repository accepts at most three HTTPS redirects, caps the feed at 1 MB and the merged result at 40, uses conditional validators, a 30-minute TTL, a 10-minute manual limit and last-good cache. URL, source/title and callsign/title similarity dedup remove true duplicates while preserving distinct same-callsign stories.

Home shows attributed headline/age/truth and opens the native DX Briefing. The Briefing exposes current/upcoming/saved filters, source and callsign/entity/headline search, exact article/source links, batch Logbook status, live-cluster and Calendar/NG3K matches, watchlist/history, and explicit receive review only when a matching live spot exists.

## PSK Reporter

The native direct query uses `senderCallsign` for Being Heard and `receiverCallsign` for Hearing. Sender/receiver identities are preserved; Mutual requires the same remote callsign and band inside the active window. JSONP callback, fields, frequency/band, mode, SNR and timestamps are validated.

Direction, enabled state, 15/30/60/180/360-minute window, minimum five-minute cadence, 500-row cap, band/mode/continent/callsign/minimum-SNR filters are consumed. Disable/clear cancels work, clears display state and generation-rejects late results. Home and DX use one shared provider authority with direction/window cache keys, 2 MB responses, Retry-After backoff and last-good fallback.

Home/map provide Being Heard, Hearing, Both and Mutual views. Direction paths retain the band palette; mutual is separately highlighted. Exact reports show endpoints, direction, frequency, SNR, worked/DXCC context, history and watchlist. No news or PSK result sends CAT directly; frequency changes require the existing receive-only review.

## Validation and evidence

- Focused Task 2B1 and truth tests: pass.
- Complete `testDebugUnitTest`: 271 tests, 0 failures, 0 errors, 0 skipped.
- `assembleDebug`: pass with Android SDK and rustup Cargo/Rustc paths.
- `git diff --check`: pass.
- APK: `android/app/build/outputs/apk/debug/app-debug.apk`
- APK size: 113,517,435 bytes.
- APK SHA-256: `395135acc3f664dfaf662f3c01a4e9345ec9479b48a5ca5f2c3d2d971c48e13c`

Source wiring, deterministic fixtures, unit tests and build are verified. No physical-device UI, live authenticated Wavelog, live-provider acceptance, RF behavior or CAT-device action was claimed. The worktree is required to be clean and the local/remote branch tips equal in the final post-push check.
