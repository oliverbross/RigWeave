# Native keyer and physical hotkeys v2

## Architecture and safety

`keyer/` defines immutable actions, profiles, bindings, templates, queue snapshots, typed failures, and `KeyerDispatchPort`. `ContestKeyerIntentAdapterInput` is the future Contest Core boundary; it grants no CAT, PTT, audio, or arming authority.

The queue permits one active and one pending transmit. Pending work expires after five seconds; Stop bypasses the limit; duplicate active and third transmit requests are rejected. Runtime state is never persisted. Each item binds to operating-context generation, foreground epoch, radio identity, mode, and profile. Background, disconnect, identity/mode/profile/generation change, route loss, disarm, or unsafe TX state clears work.

Voice plans reference 1–12 existing slot IDs, allow 0–500 ms inter-clip silence (80 ms default), and cap decoded duration at 45 seconds. All clips are opened and validated before `beginVoiceOperation`/PTT. One immutable PCM snapshot uses the existing `executeVoiceTxSequence`, with one PTT and one verified RX lifecycle. `send(slot)` remains a one-item adapter. Preview never PTTs.

CW templates support case-insensitive `{MYCALL}`, `{CALL}`, `{RST}`, `{RST_SENT}`, `{RST_RECV}`, `{SERIAL}`, `{EXCHANGE}`, `{GRID}`, `{REFERENCE}`, `{MODE}`, and `{BAND}`. `{TOKEN?}` explicitly permits missing optional data. Unknown/required-missing tokens fail. Whitespace normalization precedes the existing safe-character and documented 24-character `KY` checks; content is never truncated or retried. Serial formatting supports width, zeroes, overflow, and optional `0→T`, `1→A`, `9→N` cut numbers.

## Profiles, bindings, UI and privacy

The schema provides General CW, General Voice, Portable Run/Search, and Contest Run/Search-and-Pounce profiles, capped at 12 messages. Profiles and messages can be renamed/reordered; messages can be duplicated; bindings can be captured, cleared, or explicitly swapped after a conflict. General fallback is off by default and resolves only same-mode chords/messages not overridden by the active non-General profile. WAV bytes/paths stay in `VoiceMacroStore`. Stable metadata, messages, bindings, display/fallback choices, and active profile are transactionally recoverable. Queue, TX, arming, countdown, resolved QSO values, raw keys, paths, and audio are excluded.

Hotkeys default off and use foreground Compose delivery only—no accessibility service, overlay, background listener, or global registration. F1–F12 plus Shift/Ctrl/Alt are accepted when Android delivers them. Only initial key-down is accepted; repeats/key-up are ignored. Escape is reserved for active Stop. An active input-method session or modal blocks dispatch. Unbound/unavailable keys propagate. Assignment is explicit with clear and conflict swap/cancel.

The tappable horizontal strip presents chord, label, mode, profile, ACTIVE/PENDING/UNAVAILABLE text, non-colour border state, and zero/one queue count. Existing voice record/import/preview/delete remains in Macros settings alongside Keyer & Hotkeys management. Logical voice plans have an explicit preview action that composes their validated slot snapshot through the existing tablet-speaker preview authority and never enters the transmit controller.

`RepeatCqController` is an explicit-start, non-persistent scheduler domain with 2–600 second interval, 1–50 cycle cap, and 1–30 minute cap. Busy ticks are skipped and do not backlog. Runtime repeat state is not restored. WPM actions return unavailable. `CwKeyerBackend` is an extension point; WinKeyer is `NOT IMPLEMENTED`. Apple parity remains later work.

Automated evidence covers templates, serials, queue identity, focus/repeat/conflicts, repeat limits, composition, and the existing TX/RX sequence. On 2026-08-22, `:app:testDebugUnitTest` passed 463 tests in 58 suites (zero failed/error/skipped), including 27 Task A JVM test methods; `:app:lintDebug`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest` passed. Seven focused Android instrumentation methods compiled into the instrumentation APK but were not executed because no device/emulator run is authorised by this source-only task. No APK install or physical microphone, speaker, DigiRig, radio, CAT, PTT, RF, or on-air test is authorised or claimed.
