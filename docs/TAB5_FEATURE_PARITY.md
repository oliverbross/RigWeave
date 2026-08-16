# Tab5 Feature Parity

RigWeave Mobile ports the user-facing KX3 remote, logbook, and Neural DX behaviour from `kx3-tab5-remote` into native iPadOS and Android code. It does not inject fixture CAT frames, generated spots, fake spectrum, or demonstration QSOs.

| Area | Mobile implementation | Data source |
|---|---|---|
| KX3 deck | Dual VFO, RX/TX, mode, CWT indicator, S/RF/SWR meters, split/RIT/XIT/preamp/attenuator flags, AF/RF gain, bandwidth, power, and CAT controls | Live 38,400-baud CAT polling |
| Settings | Top tabs: Default, Log, Cluster, Macros, Alerts, Safety, Audio, Health, Diag, About | Native app preferences |
| Local log | SQLite QSO journal, local browse, ADIF import/export, QRZ/HamQTH enrichment, and CTY fallback | App-private tablet storage; no SD card |
| CTY.DAT | Download, validate, atomically replace, retain backup, and resolve callsign prefixes locally | App-private tablet storage |
| Wavelog | Encrypted API key, connection/time checks, station discovery/selection, durable upload queue, cursor-based full-log download, cached remote browse | Configured Wavelog API |
| DX cluster | Primary endpoint plus two ordered fallbacks | Configured live cluster endpoints |
| LIVE | Ranked current DX opportunities with tune and detail actions | Live cluster feed and CTY/DX analysis |
| SMART | Worked-state, distance, bearing, path, and propagation ranking | Shared DX analysis |
| BANDMAP | Per-band activity and opportunity presentation | Shared DX snapshot |
| PULSE | Twelve time buckets for each band | Shared band timeline |
| WORLD | 5 by 12 activity heat grid and regional pulse bars | Shared world grid and regions |
| WATCH | Watchlist-specific graphical activity and tune actions | Live cluster feed and configured watchlist |

## iPad acceptance pass

1. Enable the embedded Prolific KXUSB DriverKit extension, connect the PL2303GC cable and KX3, then confirm the identity and both VFOs populate without placeholder values.
2. Change mode, frequency, split, RIT/XIT, preamp, attenuator, AF/RF gain, bandwidth, and power on the radio and confirm the deck follows; use app CAT controls and confirm the radio follows.
3. Save a real QSO, relaunch, confirm persistence, export the whole ADIF file, re-import a known file, and inspect the record fields and duplicate handling.
4. Configure Wavelog, run Test Wavelog and Check time sync, load station profiles, select the intended station, upload the queued QSO, run Full log, and confirm the remote contact cache and cursor complete without duplicates.
5. Configure QRZ or HamQTH, test the selected service, enrich a known callsign, update CTY.DAT, and confirm local country fallback remains available without a network call.
6. Connect to the intended DX cluster, verify ordered fallback behaviour, and exercise LIVE, SMART, BANDMAP, PULSE, WORLD, and WATCH. Tune only a spot you intend to use.
7. Feed physical stereo I/Q audio and verify the spectrum and newest-first waterfall respond to received RF rather than an idle placeholder.

Software builds establish implementation readiness only. Driver activation, USB enumeration, live CAT semantics, authenticated Wavelog behaviour, real cluster density, and physical I/Q fidelity require this device pass.
