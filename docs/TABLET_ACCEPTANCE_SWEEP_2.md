# Tablet Acceptance Sweep 2

Frozen source: `4cd3aa3b401a9f7bc9838b94217444d5b4cae3bc` from `fix/tablet-acceptance-sweep-1`. Frozen main: `fb04d52df0c9ccc305125449bb188ef8e3f0185e`.

This branch is the operator-driven correction pass for the protected Lenovo landscape layout. It keeps QSO, Wavelog, Groups.io, radio, keyer, Digi and provider authorities unchanged.

## Delivered source changes

- Home uses one content-bounded `Shack` action.
- Contest uses schema 2 temporary entries. Logging stages locally; only the explicit restart-safe Merge to Logbook action invokes the canonical mutation coordinator. N1MM safe additions are staging-only.
- Super Check Partial uses the official `supercheckpartial.com` discovery/download contract, validates SQLite/schema/size, and atomically retains a private last-good database. Absence means unavailable, never invalid callsign.
- Band Maps v2 uses classic frequency ladders, callsign-first default labels, focal zoom, deterministic collision lanes, 160m–23cm selection, region/jurisdiction truth, and an optional shared-controller Radio ladder.
- Intelligence uses equal KPI bounds, stable category colours, count-derived heatmap intensity/legend, persistent map camera, bounded coordinate rows and full-screen map presentation.
- Portable labels are coordinate-anchored. Places exposes official IOTA JSON plus truthful blocked/import states for WWBOTA, WWFF, Castles and Lighthouses.
- Groups.io supports foreground automatic download defaults, per-group overrides and an optional top-right new-message excerpt. There is no permanent background poller.

## Evidence boundary

Source/build/test evidence does not prove physical presentation, authenticated services, RF, audio, CAT, PTT or TUNE. The live checklist records those layers separately. No provider directory, SCP database, screenshot evidence or private operator data is bundled.

