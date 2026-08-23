# QMX/QMX+ live acceptance

Physical acceptance is intentionally pending and is not required for source PASS.

## Required bench equipment

- a powered QMX and a QMX+ where available;
- known firmware identities spanning supported 1.03 and 1.04 behavior;
- Android device with OTG/UAC support;
- receive signal or generator for axis/image checks;
- dummy load, power/SWR reference and explicit operator TX authorization for later transmit tests.

## Pending checks

- exact USB function selection with one, two and three CDC configurations;
- disconnect/reconnect to the identical unit and refusal of a different unit;
- Q9/Q3 readback, including delayed write echoes and 1.04 failure behavior;
- 48 kHz stereo I/Q orientation, +12 kHz IF, CW offset, passband and tap tuning;
- image rejection improvement and steady-state correction across band/profile changes;
- QMX+ internal GPS versus paddle/host source truth;
- extra-CDC 80×24 terminal, 0x7f backspace, explicit close and primary CAT continuity;
- RF/AF gain readback, meter calibration, power and SWR units;
- FT8/FT4 cadence, PTT confirmation, abort, high-SWR trip and RX-unconfirmed handling under the central safety adapter.

No APK may be installed as part of this branch. No transmit test is authorised by this document.
