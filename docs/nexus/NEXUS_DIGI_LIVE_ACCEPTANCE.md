# Nexus Digi v2 live acceptance — Lenovo tablet

This checklist is intentionally unperformed by the source integration run.
Preserve existing app data, verify the installed package, take the required
protected backup, and use upgrade install only when separately authorized.

- [ ] Upgrade without clearing app data; existing station, credentials, presets,
  Wavelog queue and log remain intact.
- [ ] Select exact USB RX/TX routes; verify level, silence, clipping and route
  loss truth.
- [ ] Verify the waterfall uses real audio and each cursor changes the consumed
  decoder frequency.
- [ ] Receive FT8; manually call and complete one station-locked sequence; verify
  bystanders do not redirect it.
- [ ] Repeat slot/timing/STOP checks on FT4.
- [ ] Verify RTTY normal/reverse, click-to-net, transcript limit and one-shot TX
  into a safe load/test arrangement.
- [ ] Verify BPSK31 carrier acquisition/reacquire, transcript limit and one-shot
  TX into a safe load/test arrangement.
- [ ] Verify CW pitch/WPM RX and one-shot TX into a safe load/test arrangement.
- [ ] Verify SSTV RX progress, gallery metadata/share/pin/delete, exact prepared
  TX preview and TX into a safe load/test arrangement.
- [ ] Explicitly enable an ISS pass session; review 145.800 MHz FM receive tune;
  verify AOS RX, LOS stop and that TX remains unavailable.
- [ ] Where available, repeat RX/TX safety checks through Flex network audio.
- [ ] Review a Digi QSO draft, save through the canonical log, confirm the
  existing Wavelog outbox and authenticated delivery.
- [ ] Verify WSJT-X Heartbeat/Status/Decode/QSO Logged with a real peer and safe
  Halt/Clear/Replay handling; verify LAN remains blocked without opt-in.
- [ ] Background/foreground the app, unplug/replug the exact route and change
  radio frequency; verify TX never resumes.
- [ ] Across all checks, verify no unexpected PTT or TUNE and explicit STOP
  returns the radio to RX.

Record device build, package/version, APK SHA-256, radio/audio hardware,
frequency/load setup, timestamps and observable results. A build log alone is
not acceptance evidence.
