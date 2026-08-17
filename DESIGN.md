# RigWeave Flightline design system

## Direction

RigWeave is one coherent field instrument across SwiftUI, Compose, and the planned Qt/QML desktop client. The visual language is shared; navigation, focus, dialogs, menus, keyboard, pointer, windowing, and accessibility follow each platform.

The instrument is a low-glare graphite chassis around an amber measurement surface. Off-white carries primary labels, yellow carries secondary/hold meaning, green signals healthy observed state, and red is reserved for safety and transmit state. Colour is always reinforced by text, shape, symbol, or state wording.

Dense radio information is intentional. Do not turn the operator console into a grid of decorative dashboard cards. Spectrum and waterfall are operational instrumentation where they are implemented, not ambient animation.

## Durable rules

- Live radio truth dominates hierarchy: dual VFO, mode, split/RIT/XIT, meters, gain, bandwidth, power, and connection state remain legible.
- Interpret the KX3 faceplate without copying its physical limitations. Keep the amber hierarchy, raised control language, off-white primary labels, and yellow secondary labels.
- Use platform-appropriate touch targets and accessibility APIs. Android targets at least 48 dp; Apple follows current Human Interface Guidelines and Dynamic Type/VoiceOver behaviour.
- Compact, portrait, landscape, multi-window, keyboard, and pointer layouts must reveal unavailable controls rather than silently clipping them.
- Potentially transmitting controls require explicit state, confirmation where appropriate, and an immediately available abort/Emergency RX path.
- No colour-only status, fabricated signal animation, placeholder QSO, or generated spectrum.
- The panadapter axis follows live CAT and the physical audio sample rate. Empty/offline input stays visibly offline.
- Android monitor, panadapter, EQ capture/playback, voice record/import/preview, and voice TX are mutually exclusive audio owners. Only the monitor may be paused and restored by the central coordinator; no other owner is preempted.
- Future desktop panels may resize or detach, but no desktop implementation is implied by this contract.
- Before public binary distribution, an About/Licences surface must make GPL and applicable third-party notices readily accessible.

## Current surface truth

Android implements the KX3-style console, the broader Neural DX workspace, Portable Chase, and POTA Activate. Apple implements a native iPad-focused navigation and radio/log/DX/panadapter flow. Their destination sets need not be forced into artificial parity; durable behaviour and evidence must remain clear.

On Android, Panadapter is a first-class expanded destination and a segmented Radio subview on compact layouts so the existing six-item bottom navigation remains stable. The Flightline instrument keeps spectrum/waterfall dominant and unscrolled, offers a draggable split or either pane alone, and separates view gestures from explicit marker QSY actions. Setup and diagnostics may scroll; the live instrument does not.

- Scene: field and station operation in mixed or low ambient light requires a dark, low-glare chassis with a high-contrast warm instrument face.
- Color strategy: restrained graphite application shell plus a committed amber radio region. Amber is measurement surface, not decorative accent.
- Core colors: graphite `#111519`, panel `#1B2228`, raised `#283139`, line `#4A555D`, ink `#F4F0E7`, muted `#A5ADB2`, amber `#E9A72B`, amber-dark `#201708`, hold `#F4C94E`, healthy `#42C77B`, danger `#E4544D`, split `#8F1D24`.
- Typography: Android system sans for navigation, forms, and actions; monospace only for frequency, CAT, meters, time, and tabular measurements.
- Shape: 8–12 dp instrument and panel radii; small state chips may be pill-shaped. Dense operational content uses regions and tables, not nested decorative cards.
- Controls: every paired control presents primary tap text and secondary yellow hold text. Minimum target 48 dp with clear pressed, disabled, armed, pending, and error states.
- Navigation: Material navigation rail/drawer on expanded width; navigation bar on compact width. The compact bar retains Home, Radio, Logbook, Presets, DX, and Settings. EQ and Portable are first-class expanded destinations; compact layouts reach EQ through Radio/Settings Audio and Portable Chase through Home.
- Portable Chase: wide tablets use programme/status controls, a filter rail, stable ranked activity, and a shared map/detail cockpit; compact layouts use On Air, Map, and Places destinations. Graphite surfaces, restrained provider marker colours, explicit independent freshness, and selection-before-tune keep the multi-program decision path readable without turning rows into bright cards.
- POTA Activate: Portable owns a CHASE/ACTIVATE mode switch rather than another top-level destination. Expanded tablets pair a fixed session/logger panel with progress and recent-QSO context; compact layouts use one predictable vertical surface. A slim, non-transmitting active-session strip appears on Radio, Portable, and Logbook.
- EQ Studio: a Flightline audio bench, not a music-player equalizer. Graphite instrument regions hold exact green radio readback, yellow local draft state, amber response/measurement plots, real waveform/spectrum data, source/baseline provenance, touch-safe eight-band controls, and a fixed apply-and-verify action. Compact layouts stack the same workflow without shrinking the controls into narrow faders.
- Motion: short Material fade-through/shared-axis transitions only where state or destination changes. No decorative entrance choreography.
- Safety: transmit state overrides ordinary color and hierarchy with redundant red text, full-width warning, and permanent emergency RX access.
- Tables: fixed semantic columns, alternating tonal rows, sticky context where possible, explicit empty/loading/error recovery, and 20–25 row paging rather than unbounded rendering.
- Logbook: a fixed-header, horizontally scrollable flightline table is the primary surface. Logbook and Filters are peer tabs; General and QSL filter groups use adaptive native fields, fixed bottom actions, row selection for quick filters, and explicit Local/Wavelog station provenance.
- Sync Hub: reached from Logbook or Settings rather than top-level navigation. Expanded layouts pair restrained provider cards with the outbox; compact layouts stack the same authority banner, configuration, filters, and item actions without horizontal overflow. Text and symbols reinforce every queue, attention, and accepted state.
- Responsive: expanded landscape restructures into an instrument console; compact layouts preserve every core action through vertical regions and horizontal control strips rather than scaling the Tab5 geometry.
- Radio console: interpret the physical Elecraft KX3 faceplate rather than arranging generic application cards. The amber LCD follows the original KX3 hierarchy: a compact seven-segment VFO A in the upper-right, a smaller VFO B below it, boxed A/B and mode/TX indicators at the edge, separate S/CWT and SWR/RF meters at upper-left, the filter trapezoid below, and dense ANT/ATU/RIT, AGC/PRE/ATTN/CWT/XFIL/BW truth between them. Compact 4 × 3 key banks flank the display and the twelve receive keys form one strip below; every key uses the real radio's raised rounded gray cap, beveled tonal face, bright edge, off-white primary legend, and yellow secondary legend. The lower-right tuning deck mirrors the three non-VFO rotary groups: AF/RF/MON, PBT I/WID plus PBT II/SHT and NORM, and KEYER/MIC/PWR, with live CAT values for each direct control. Tapping VFO A opens 160–10 m band recall, tapping the mode legend opens supported non-digital modes, and tapping BW opens the six Tab5-derived widths appropriate to the active mode. The VFO tracks horizontal drag smoothly in both directions and sends frequency changes continuously during rotation, while retaining Android semantics and touch targets.
- Transmit safety: there is no persistent `TX DISABLED` rail. Potentially transmitting controls retain their existing per-action confirmation and Emergency RX remains continuously available.

The Phase 0 documentation and licensing work changed no navigation or operator-facing UI.
