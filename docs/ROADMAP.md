# RigWeave roadmap

This is the normative product order. A phase starts only after separate owner authorisation and the previous gate is reviewed. Dates and effort estimates are intentionally absent.

## Phase 0 — Repository truth and product alignment

- Reconcile active contracts and historical evidence.
- Adopt GPL-3.0-only and record corresponding-source obligations.
- Audit direct dependencies, incorporated material, service terms, and provenance risks.
- Inspect Nexus at an immutable commit without importing code.
- Establish source-backed feature and build baselines.
- Record architecture boundaries and one next candidate phase.

**Gate:** Contracts agree, applicable builds pass or have exact blockers, no Nexus code is imported, distribution risks are bounded, branch is reviewed.

## Phase 1 — KX3/KX2 Studio

### Phase 1A — Panadapter completion and audio-source architecture

- Audit and harden the existing physical-I/Q implementation rather than rewrite it.
- Complete device selection and persistence.
- Measure I/Q orientation, image rejection, and calibration.
- Verify CAT/spectrum alignment and rendering/interaction parity.
- Provide the foundation for operating profiles and later spot overlays.

**Excludes:** EQ, voice macros, portable-programme feeds, generic radio abstractions, and Nexus integration.

### Phase 1B — RX/TX EQ Studio and operating profiles

- Real KX3/KX2 EQ read/write.
- RX/TX profiles and microphone/audio-device association.
- Safe before/after monitoring.
- Restore/rollback semantics.
- Profile foundation shared with macros and portable operation.

### Phase 1C — Voice macros

- Recorded/imported audio, preview, and session variables.
- Digirig-compatible routing only where physically supported.
- Explicit PTT, timeout, abort, and no unattended transmission.

### Android Phase 1 closure status

- Phase 0 is complete.
- Android Phase 1A/1B/1C are implemented and integrated behind one exclusive audio-owner contract.
- Android panadapter software/device behavior is fail-closed; physical KX3 quadrature-I/Q RF acceptance remains deferred.
- iPadOS KX3 Studio parity for EQ Studio and SSB voice macros is the next candidate.
- The owner deferred iPadOS Studio parity and explicitly authorised Android POTA Chase as Phase 2A. This supersedes the former next-candidate ordering without rewriting Phase 1 evidence.

## Phase 2 — Portable Chase v1

- **Phase 2A implemented on Android:** POTA live activator feed; honest live/cache/stale states; source-stamped replaceable park database; offline search and nearby; local worked labels; deterministic recommendation reasons; joined list/map selection; operator-confirmed receive-only tune; and the existing logger/Wavelog handoff.
- **Phase 2B integrated — PASS WITH EXTERNAL DEPENDENCY:** unified POTA/SOTA/WWFF model and UI, WWFF Spotline/agendas, staged offline SOTA summits, grouping, programme-specific worked state, map/Places, and multi-reference Tune & Log are present. SOTA live remains disabled pending written approval for the replacement API; RigWeave makes no request to an unapproved or deprecated endpoint.
- WWFF full offline directory storage is deferred pending programme permission. iPadOS Portable Chase parity remains deferred.
- Phase 3A POTA Activate is implemented on Android. Panadapter spot overlays remain deferred.
- No Nexus source or dependency is incorporated.

**Gate:** Data access/licensing confirmed; no false universal coverage.

## Phase 3 — Portable Activate v1

- **Phase 3A implemented on Android:** recoverable POTA sessions, explicit boundary acknowledgement, CAT-optional fast logger, multi-own-park metadata, P2P handoff, UTC progress/rollover, programme-correct ADIF fan-out, file sharing, and official-site spotting/upload handoffs.
- SOTA/WWFF activation remains later work. Direct programme spotting/log upload APIs remain excluded; no universal upload route is claimed.
- iPadOS parity remains deferred. No Nexus source or dependency is incorporated.

## Phase 4 — Sync and Progress v1

- Multi-destination outbox for local-log mode.
- Stage QRZ, Club Log, and eQSL.
- Preserve Wavelog authority mode and prevent duplicate uploads.
- Track worked, uploaded, accepted, and confirmed states.
- Add Needs Board and useful activation/award statistics.
- Keep direct LoTW signing later because certificate/key handling is materially different.
- Review any Nexus connector, queue, logbook, or award component before reuse.

## Phase 5 — FlexRadio SmartLink

### Phase 5A — Legitimate authentication, discovery, and control

- Use an official developer path and legitimate SmartLink authentication.
- Secure token storage, discovery/brokering, recovery, slice/frequency/mode state, tune/log integration.
- Revalidate Nexus protocol candidates against current official interfaces; never reuse another application's client identity or credentials.

### Phase 5B — Spectrum, waterfall, and meters

- Official TCP/UDP/VITA-49 interfaces.
- Panadapter/waterfall streams, meters, and RigWeave overlays.

### Phase 5C — Remote audio and controlled TX

- Only after 5A/5B physical proof.
- Explicit operator control and safe recovery.
- No attempt to clone every SmartSDR or StationPilot feature.

## Phase 6 — Desktop client

### Phase 6A — Qt 6/QML/CMake foundation

- One Qt/QML desktop UI consuming the shared C++ core.
- Desktop-specific adapters and the Flightline design language.
- Nexus Tauri/React remains reference-only.
- A selected audited Rust static library with a narrow C ABI is permissible only when a real component justifies the extra runtime boundary.

### Phase 6B — macOS

### Phase 6C — Windows

### Phase 6D — Linux

Each platform gate covers packaging, credentials, serial/audio adapters, and physical validation without forking the desktop UI unnecessarily.

## Phase 7 — Broader radio and programme ecosystem

- QMX only when hardware is available.
- rigctld compatibility where useful.
- IOTA, beaches, bunkers, lighthouses, and other programmes only after data/licence review.
- No promise of universal rig or programme parity.
