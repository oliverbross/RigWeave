# RC1 Feature Completion Matrix

`FOUNDATION_WIRED = 0`; `MISSING = 0`. Live-dependent rows remain explicitly pending rather than being inferred from source or simulator results.

| Feature group | Status | Acceptance boundary |
| --- | --- | --- |
| QSO database, mutation, projection | SOURCE_COMPLETE | migration/transaction/identity suites |
| Wavelog | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | authenticated tenant round trip |
| cluster and spot repository | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | live cluster stream |
| provider/cache and Neural outlook | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | live provider truth/outage behavior |
| Home/HamClock | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | live network rendering |
| radio, Hamlib and TCI | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | physical CAT/readback; no inferred TX authority |
| audio | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | physical route/capture/playback |
| panadapter/waterfall | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | live stream and visual acceptance |
| Digi | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | audio + CAT/PTT/TUNE/RF are separate gates |
| Keyer | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | physical keying/RF excluded from RC |
| Contest and N1MM | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | trusted live peer acceptance |
| DX Chaser and Band Maps | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | live provider/radio acceptance |
| Portable and Operations | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | authenticated/live workflows |
| Satellite | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | live ephemeris, radio and rotator |
| Groups.io | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | authenticated account acceptance |
| rotator | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | physical motion excluded from RC |
| configuration and credential vault | SOURCE_COMPLETE | golden import/export, future-section and secret-exclusion gates |
| alerts, health and support bundle | SOURCE_COMPLETE | deterministic privacy/size/redaction gates |
| operating context, workspace actions and global Stop | SOURCE_COMPLETE_LIVE_ACCEPTANCE_PENDING | platform lifecycle and physical-control acceptance |
