# RC1 Live Acceptance Checklist

No unchecked item is implied by a source, build, simulator, fake-service or hosted-CI pass.

- [ ] unlocked Android/iOS visual acceptance at required profiles
- [ ] Windows/macOS physical-display visual acceptance and accessibility review
- [ ] authenticated Wavelog, Groups.io, callbook and provider workflows using non-production test data
- [ ] live cluster, TCI and panadapter/waterfall streams
- [ ] physical input/output audio routing and teardown
- [ ] CAT connection/capability/readback with transmission controls initially disabled
- [ ] separately authorized PTT/TUNE behavior into a dummy load; no on-air inference
- [ ] RF acceptance under operator control
- [ ] rotator connection/readback and separately authorized physical movement
- [ ] N1MM trusted-peer workflow and safe staging review
- [ ] satellite ephemeris/radio/rotator end-to-end workflow
- [ ] Apple DriverKit transport on approved hardware
- [ ] protected TB373FU in-place install only if identity, package, UID, schema and backup gates pass
- [ ] release signing, notarization and distribution under explicit owner authority

The global Stop action must be exercised before any physical-control acceptance. Unknown capability remains unknown; failed readback leaves controls inert.
