# MSHV Auto DX Chaser upstream watch

`scripts/check_mshv_auto_dx_chaser_upstream.py` compares upstream `master` with the reviewed commit/tree and verifies the pinned
GPL-3.0 and third-party licence digests. It inventories watched changes in the documented engine, scoring, decode cache, spot,
band, settings, DXCC, UI and `main_ms.*` integration paths.

Classifications are `SCORING`, `TARGET_SELECTION`, `LOCAL_DECODE`, `STATE_MACHINE`, `PERSISTENCE`, `BAND_SWITCHING`, `SAFETY`,
`SETTINGS`, `PROVIDER`, `DXCC_TRACKING`, `UI`, `LICENCE_PROVENANCE`, `MSHV_DESKTOP_ONLY` and `DOCUMENTATION`. A path may receive
multiple applicable labels; every classification retains the exact changed path in JSON.

Outputs are `build/reports/mshv-auto-dx-chaser-upstream.json` and `.md`. Exit 0 means the reviewed identity is unchanged, exit 1
means an honest comparison could not be completed, and exit 2 means review is required. The weekly/manual workflow uploads the
reports and fails on exit 1 or 2. It has read-only repository permission, never edits production source, never updates a pin and
never opens a pull request.
