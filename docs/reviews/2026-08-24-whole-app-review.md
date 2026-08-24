# Whole-application review — 2026-08-24

## Scope and evidence boundary

Three independent whole-source reviews examined the Android, Apple, shared C++, networking, persistence, radio-control, digital-mode, contest, Groups.io, Wavelog, and release-support paths. Findings were merged, checked against the source, and repaired on `integration/rigweave-final-whole-app-v1`.

This review includes source inspection, native tests, Android production compilation and unit tests, an Apple simulator build, release-policy checks, provenance tests, and script/data syntax checks. It does not claim physical tablet, iPad, radio, audio, authenticated-service, network-peer, or RF evidence. No application was installed or deployed and no radio was connected.

## Correctness and safety repairs

- Made C ABI CAT numeric parsing non-throwing for values outside `int` range and added malformed-number regression coverage.
- Restored the canonical `APP_KX3TOUCH_UUID` ADIF identity while retaining the legacy RigWeave alias on import.
- Replaced the Apple ADIF character-index parser with a UTF-8 byte-length parser and retained non-reserved fields.
- Made Apple Fast Entry import and undo transactional; Wavelog queue changes now happen only after a successful commit.
- Changed Apple SQLite text binding to `SQLITE_TRANSIENT` so bound text outlives temporary Swift bridges.
- Preserved Wavelog ambiguous-create state across later edits, prevented deletion after an attempted create, stopped blind retry after ambiguous legacy POST outcomes, and made queue corruption/persistence failures visible and fail-closed on both platforms.
- Preserved local ADIF extensions during Android remote merge.
- Replaced destructive SQLite `CONFLICT_REPLACE` parent writes for Contest sessions, Groups.io topics, and Groups.io drafts so child reservations, links, messages, and attachments survive updates.
- Serialized Groups.io outbox processing and re-read durable state before delivery to prevent duplicate foreground posts.
- Stopped forwarding Groups.io bearer credentials to transient attachment/CDN URLs.
- Added bounded WAV ingestion and overflow-safe RIFF chunk arithmetic; synchronized native modem teardown with feed calls.
- Closed Digi double-send preparation races and made stop/recovery remain `RX_UNCONFIRMED` until telemetry or CAT confirms receive.
- Made Flex MOX/TUNE/CWX state telemetry-driven instead of treating a successful command write as proof of transmission or receive.
- Added one-shot confirmation for transmit-capable Apple CAT commands and best-effort `RX;` before disconnect, reconnect, and teardown.
- Made SmartLink first-use certificates require explicit fingerprint review instead of automatic trust-on-first-use.
- Validated cluster callsigns before line-protocol framing on Android and Apple.
- Validated NTP response source, shape, mode, stratum, originate timestamp, transmit timestamp, and bounded offset before persisting clock skew.
- Rolled back N1MM active state and sockets when startup or bind fails.
- Made Android USB disconnect complete before the disappearing Compose scope is cancelled.
- Connected previously inert Contest trusted-LAN and export-validation actions to real behavior.
- Excluded Keystore-dependent encrypted preference files from Android backup/device transfer so unrestorable ciphertext is not migrated.
- Removed internal task/phase copy from shipping error messages and corrected the product contract’s stale digital-mode statement.

## Follow-up backlog

The review intentionally leaves these as explicit follow-ups rather than presenting them as completed:

- Replace remaining Android MapLibre annotation APIs, old Material tab/icon APIs, and Kotlin intersection-type inference before their upstream deprecations become build errors.
- Replace source-substring “safety tests” with behavioral fakes around Digi CAT/audio ownership and add hosted Apple/unit plus Android instrumentation jobs to the release workflow.
- Finish the CALLBOOK workspace handoff so a lookup result opens a concrete profile instead of only routing to Radio.
- Add the production N1MM trust-management/editor surface and a deliberately scoped typed-command bridge; current defaults remain fail-closed and monitor-only.
- Stream Digi raw recording writes in blocks and expose dropped-frame accounting under slow storage.
- Add device tests for oversized Groups.io archive/import cleanup and database child-row preservation.
- Complete physical and authenticated acceptance separately: protected tablet/iPad UI, USB/radio CAT, DigiRig/Flex audio, SmartLink/Groups.io/Wavelog services, and RF behavior.

## Validation

- Shared C++ Debug build: passed.
- CTest: 2/2 passed, including the malformed numeric CAT regression.
- Android `compileDebugKotlin testDebugUnitTest`: passed (`BUILD SUCCESSFUL`, 19m 42s).
- Apple `RigWeave` Debug simulator build with signing disabled: passed.
- Release-candidate policy audit: passed.
- OpenHamClock upstream checker tests: 7 passed.
- Python compile and repository JSON parsing: passed.
- `git diff --check`: passed.

