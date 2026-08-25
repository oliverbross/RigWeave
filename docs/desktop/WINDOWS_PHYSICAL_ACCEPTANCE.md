# Windows physical acceptance checklist

This checklist is pending; no physical Windows installation or hardware operation is part of the integration task.

- [ ] Install/uninstall per-user NSIS package; launch portable ZIP.
- [ ] Verify window, keyboard, pointer, scaling and saved layout.
- [ ] Exercise fake Wavelog, then authenticated live Wavelog with a test station.
- [ ] Connect live cluster, request `SH/DX`, and confirm the one spot repository feeds DX and Band Maps.
- [ ] Verify callsign-first labels, frequency truth, bounded retention, filter reset and all-filtered explanation.
- [ ] Open Hamlib dummy read-only, then a real radio read-only with explicit operator approval.
- [ ] Open rotator read-only; do not move until separately approved.
- [ ] Verify Panadapter receive-only from an explicitly selected stereo/IQ device; no microphone fallback.
- [ ] Repeat shutdown/restart with network, audio and dummy sessions active.
- [ ] Preview/import/export configuration and confirm credentials/TX/motion authority are absent.
- [ ] Generate and inspect a sanitized support bundle.

PTT, TUNE, RF and automatic rotator motion remain out of scope.
