# Tab5 Feature Parity

RigWeave Mobile ports the user-facing KX3 remote, logbook, and Neural DX behaviour from `kx3-tab5-remote` into native iPadOS and Android code. It does not inject fixture CAT frames, generated spots, fake spectrum, or demonstration QSOs.

| Area | Mobile implementation | Data source |
|---|---|---|
| KX3 deck | Dual VFO, RX/TX, mode, CWT indicator, S/RF/SWR meters, split/RIT/XIT/preamp/attenuator flags, AF/RF gain, bandwidth, power, and CAT controls | Live 38,400-baud CAT polling |
| Local log | SQLite QSO journal, local browse, ADIF serialization, whole-log `.adi` export | Device database |
| Wavelog | Encrypted API key, station discovery/selection, durable upload queue, cursor-based full-log download, cached remote browse | Configured Wavelog API |
| LIVE | Ranked current DX opportunities with tune and detail actions | Live cluster feed and CTY/DX analysis |
| SMART | Worked-state, distance, bearing, path, and propagation ranking | Shared DX analysis |
| BANDMAP | Per-band activity and opportunity presentation | Shared DX snapshot |
| PULSE | Twelve time buckets for each band | Shared band timeline |
| WORLD | 5 by 12 activity heat grid and regional pulse bars | Shared world grid and regions |
| WATCH | Watchlist-specific graphical activity and tune actions | Live cluster feed and configured watchlist |

## iPad acceptance pass

1. Enable the embedded CP210x DriverKit extension, connect the adapter and KX3, then confirm the identity and both VFOs populate without placeholder values.
2. Change mode, frequency, split, RIT/XIT, preamp, attenuator, AF/RF gain, bandwidth, and power on the radio and confirm the deck follows; use app CAT controls and confirm the radio follows.
3. Save a real QSO, relaunch, confirm persistence, export the whole ADIF file, and inspect the record fields.
4. Configure Wavelog, load station profiles, select the intended station, upload the queued QSO, run Full log, and confirm the remote contact cache and cursor complete without duplicates.
5. Connect to the intended DX cluster and exercise LIVE, SMART, BANDMAP, PULSE, WORLD, and WATCH. Tune only a spot you intend to use.
6. Feed physical stereo I/Q audio and verify the spectrum and newest-first waterfall respond to received RF rather than an idle placeholder.

Software builds establish implementation readiness only. Driver activation, USB enumeration, live CAT semantics, authenticated Wavelog behaviour, real cluster density, and physical I/Q fidelity require this device pass.
