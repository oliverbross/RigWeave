# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Amateur-radio operators using an Android phone or tablet as a direct field console for an Elecraft KX3 or KX2. The primary tablet posture is landscape, touch-first operation beside the physical radio; compact and multi-window layouts must remain usable.

## Product Purpose

RigWeave provides observed live radio control, local-first QSO logging, Wavelog synchronization, and analyzed DX awareness through native Android hardware and services. Success means an operator can control and monitor the connected radio, log safely without network dependence, and act on real DX data without simulated fallback.

## Positioning

RigWeave is the native mobile successor to the M5Stack Tab5 KX3 Touch Remote: one hardware-first operating console built around a portable C++ radio/DX core and Android USB Host integration.

## Operating Context

- Elecraft KXUSB/Prolific PL2303-family serial connection, normally 38,400 baud.
- KX3/KX2 field, portable, and station operation in changing light and network conditions.
- Local SQLite/ADIF logging with optional Wavelog, QRZ/HamQTH, NOAA, CTY, and DX-cluster services.
- Android tablet landscape, portrait, multi-window, keyboard, and pointer use.

## Capabilities and Constraints

- Retained compact destinations: Home, Radio, Logbook, Presets, DX, and Settings. Expanded navigation also exposes EQ as a first-class destination; compact layouts open EQ Studio from Radio or Settings → Audio without adding a seventh bottom-bar item.
- Android EQ Studio reads exact KX3 RX/TX curves, keeps radio/draft/profile state separate, records one finite local audio sample, previews an approximate eight-band response with matched/blind A/B, and applies only through an exclusive CAT transaction with exact readback verification. It never keys the transmitter or claims to reproduce Elecraft's undocumented DSP topology.
- Panadapter, raw Spots destination, and Digital/WSJT-X are explicitly deferred and absent from navigation.
- The consolidated DX destination owns live cluster browsing and analyzed DX views.
- Radio state must be observed truth with explicit live, stale, disconnected, pending, and failed states.
- TX, TUNE, ATU TUNE, and CW macro transmission are disabled by default, explicitly armed, never started automatically, and never blindly retried.
- Local QSO durability outranks network synchronization; service failure degrades only that service.
- Logbook follows the configured source: the complete tablet log in Local mode, or the selected station's two-way cached Wavelog log including offline queued QSOs. New QSOs are entered only from Radio.
- Logbook filtering covers date presets, station and award fields, propagation, comments, numeric distance/duration expressions, QSL and online-service states, sorting, quick filters, and bounded result counts.
- No demo radio state, fixture spots, fabricated worked state, credentials, or automatic test QSO.

## Brand Commitments

Preserve the Tab5 Flightline instrument language: graphite chassis, amber radio display, off-white primary labels, yellow hold labels, green healthy state, and red transmit/safety state. Translate it into Material 3 behavior rather than copying embedded-device mechanics literally.

## Evidence on Hand

- Canonical source: `/Users/oliver/Documents/M5Stack Core2/kx3-tab5-remote`.
- Mobile parity contract: `docs/mobile-architecture-orchestration.md` in that source.
- Canonical visual reference: `docs/evidence/ui-comps/option-b-flightline.png` in that source.
- Current Android implementation and shared portable core in this repository.
- A physical Lenovo `TB373FU`, real KXUSB `067B:23A3`, and live KX3 are available for validation.

## Product Principles

1. Radio truth before decoration.
2. Local control and local logging before network work.
3. Dense controls remain legible, predictable, and touch-safe.
4. Transmit-capable actions are explicit, bounded, and never inferred.
5. Hardware, service, and physical-radio evidence remain separate.

## Accessibility & Inclusion

Use Material semantic roles, scalable `sp` typography, 48 dp minimum touch targets, explicit text plus color for state, meaningful content descriptions, and size-class-driven layouts.
