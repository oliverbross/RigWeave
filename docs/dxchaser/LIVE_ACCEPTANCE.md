# DX Chaser live acceptance checklist

This checklist is reserved for the later semantic integration and physical validation. No item is claimed by the core branch.

- [ ] A real live FT8 CQ decode enters the candidate list with exact station/radio/band/frequency provenance.
- [ ] A real live FT4 directed CQ decode enters the candidate list.
- [ ] Candidate ranking and reason/penalty text match the observed input snapshot.
- [ ] Assist mode emits recommendations only.
- [ ] Dry Run exercises decisions and emits no operational intent.
- [ ] Chase requires an explicit start in the current app session and does not restore after restart/import.
- [ ] Digi prepare-call integration revalidates the exact decode and uses existing TX enable/arm and FT sequence safety.
- [ ] A remote response creates an engagement lock and prevents pre-emption.
- [ ] Canonical successful QSO completion updates Chaser stats/cooldown without a second log entry.
- [ ] Failed and timed-out exchanges stop at configured finite limits.
- [ ] Route loss stops the session and clears the pending intent.
- [ ] Backgrounding stops the session and requires an explicit restart.
- [ ] Radio, station, mode and material frequency changes stop the session.
- [ ] A cross-band opportunity opens receive review only; no CAT command occurs.
- [ ] A non-standard frequency suggestion opens receive review only and still requires a new local decode.
- [ ] Wavelog logging occurs only through the canonical QSO mutation/outbox path.
- [ ] External-spot-only evidence cannot cause prepare, PTT, TUNE or QSY.
- [ ] No unexpected PTT, TUNE, TX enable, TX arm or frequency change occurs on a physical radio.

Record app build/SHA, station profile, radio, route/audio state, band/mode/frequency, decode source/slot, exact action trace and the
canonical QSO ID for each completed acceptance case. RF and on-air testing requires the operator's normal transmit authority and
safety procedures.

