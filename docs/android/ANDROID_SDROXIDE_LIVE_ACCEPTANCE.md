# Android SDRoxide Live Acceptance

## Deterministic source acceptance

- Debug Lab can create two fake receiver states, fake I/Q, and fixture RF observations.
- Fake data is labelled `DEMO · NO RADIO` and performs no external network or radio action.
- JVM, instrumentation compilation, native build, lifecycle, and boundedness evidence may be accepted without physical RF.

## Physical acceptance

Physical TCI connection requires a real server and explicit operator authority for receive-only testing. PTT, TUNE, TX audio, and TX chrono remain prohibited.

Tablet acceptance requires the protected-device sequence: confirm installed package, take and hash a fresh private-data backup, install only with `adb install -r`, then verify UID, data/schema, launch/process, crash evidence, and unlocked screenshots. Build output alone is not device acceptance.
