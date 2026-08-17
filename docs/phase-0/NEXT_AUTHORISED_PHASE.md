# Next authorised phase gate

## Candidate

**Phase 1A — KX3/KX2 panadapter completion and audio-source architecture**

This is the single next candidate because real shared DSP, Apple/Android audio paths, and historical physical iPad I/Q evidence already exist. Hardening that working slice reduces risk for EQ profiles, voice macros, portable overlays, and later radio integrations without introducing a speculative abstraction.

## Required owner authorisation

Phase 0 does not authorise Phase 1A. A later task must explicitly approve:

- target client(s) and exact acceptance devices;
- audio interfaces and radio topology available for physical testing;
- whether installation on the named devices is permitted;
- the calibration source/measurement method;
- any allowed operator-facing UI changes.

## Phase 1A scope

- inspect existing capture, persistence, reversal, DSP, axis and rendering paths;
- preserve the working KX3/KX2 implementation;
- fix only source-backed panadapter/audio defects;
- add focused tests for selected defects;
- physically verify input selection, stereo I/Q, orientation, image rejection, CAT/axis alignment, monitoring, persistence, and disconnect/recovery where equipment is available.

## Exclusions

No EQ, voice macro, portable-programme, FlexRadio, QMX, desktop, Rust, Nexus-source import, schema change, release, or public distribution.

## Gate

Pass only with separate evidence for build, physical audio, real radio/CAT alignment, rendering, persistence, and failure recovery. Hardware unavailable means partial evidence, not fabricated proof.
