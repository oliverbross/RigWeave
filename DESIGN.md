# RigWeave Flightline Design System

## Direction contract

**THESIS** — RigWeave is a wide field instrument where live radio truth dominates; it refuses the generic dashboard of interchangeable cards.

**OWN WORLD** — A graphite chassis frames one amber instrument display. Off-white tap labels, yellow hold labels, green observed health, and red transmit safety create a stable operational vocabulary.

**STORY** — Confirm CAT and frequency first, operate from tactile control banks, keep logging within reach, and move into DX or service detail without losing radio state.

**FIRST VIEWPORT** — On expanded landscape, status, dual VFO truth, meters, essential controls, logging, and tuning form one no-scroll radio console beside persistent navigation.

**FORM** — Android Material 3 structure carries the Tab5 Flightline composition. Navigation and dialogs remain native; the instrument face, control pairs, state rails, and dense tables preserve the source grammar.

## Durable visual rules

- Scene: field and station operation in mixed or low ambient light requires a dark, low-glare chassis with a high-contrast warm instrument face.
- Color strategy: restrained graphite application shell plus a committed amber radio region. Amber is measurement surface, not decorative accent.
- Core colors: graphite `#111519`, panel `#1B2228`, raised `#283139`, line `#4A555D`, ink `#F4F0E7`, muted `#A5ADB2`, amber `#E9A72B`, amber-dark `#201708`, hold `#F4C94E`, healthy `#42C77B`, danger `#E4544D`, split `#8F1D24`.
- Typography: Android system sans for navigation, forms, and actions; monospace only for frequency, CAT, meters, time, and tabular measurements.
- Shape: 8–12 dp instrument and panel radii; small state chips may be pill-shaped. Dense operational content uses regions and tables, not nested decorative cards.
- Controls: every paired control presents primary tap text and secondary yellow hold text. Minimum target 48 dp with clear pressed, disabled, armed, pending, and error states.
- Navigation: Material navigation rail/drawer on expanded width; navigation bar on compact width. Retained destinations are Home, Radio, Controls, Logbook, Presets, DX, Settings.
- Motion: short Material fade-through/shared-axis transitions only where state or destination changes. No decorative entrance choreography.
- Safety: transmit state overrides ordinary color and hierarchy with redundant red text, full-width warning, and permanent emergency RX access.
- Tables: fixed semantic columns, alternating tonal rows, sticky context where possible, explicit empty/loading/error recovery, and 20–25 row paging rather than unbounded rendering.
- Responsive: expanded landscape restructures into an instrument console; compact layouts preserve every core action through vertical regions and horizontal control strips rather than scaling the Tab5 geometry.
- Radio console: interpret the physical Elecraft KX3 faceplate rather than arranging generic application cards. The amber LCD follows the original KX3 hierarchy: a compact seven-segment VFO A in the upper-right, a smaller VFO B below it, boxed A/B and mode/TX indicators at the edge, separate S/CWT and SWR/RF meters at upper-left, the filter trapezoid below, and dense ANT/ATU/RIT, AGC/PRE/ATTN/CWT/XFIL/BW truth between them. Compact 4 × 3 key banks flank the display and the twelve receive keys form one strip below; every key uses the real radio's raised rounded gray cap, beveled tonal face, bright edge, off-white primary legend, and yellow secondary legend. The lower-right tuning deck mirrors the three non-VFO rotary groups: AF/RF/MON, PBT I/WID plus PBT II/SHT and NORM, and KEYER/MIC/PWR, with live CAT values for each direct control. Tapping VFO A opens 160–10 m band recall, tapping the mode legend opens supported non-digital modes, and tapping BW opens the six Tab5-derived widths appropriate to the active mode. The VFO tracks horizontal drag smoothly in both directions and sends frequency changes continuously during rotation, while retaining Android semantics and touch targets.
- Transmit safety: there is no persistent `TX DISABLED` rail. Potentially transmitting controls retain their existing per-action confirmation and Emergency RX remains continuously available.
