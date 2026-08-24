# Tablet Acceptance Sweep 3 live checklist

Run only after local and exact-SHA hosted gates pass and after the protected backup manifest is complete.

- [ ] Cluster disconnected control state captured.
- [ ] Cluster connected status/endpoint/duration/counters captured without credentials.
- [ ] Explicit SH/DX response reaches Radio, Band Maps and Contest.
- [ ] Radio profiles/Add Radio and Hamlib search captured; no radio connection required.
- [ ] Rotator settings captured; do not connect or move hardware.
- [ ] Day/Night/Field, Contest globals, Band Map autosave, Groups.io overrides, two-column Health and About captured.
- [ ] SCP highlighting, Contest spot table/Band Map and unique Contest header captured.
- [ ] Contact Map labels, Portable anchored detail/no route jump, panned Operations viewport, WWFF split state and blocked overlays captured.
- [ ] Debug Groups.io alert Open/Dismiss/Mute captured; no real-message wait required.
- [ ] `adb install -r` only; UID/private hashes/schema/QSO/projection counts preserved.
- [ ] Process survives 180 seconds, crash buffer is empty, and relaunch succeeds.

Physical RF/audio, authenticated service success and rotator movement remain separate evidence and are not inferred.
