# RGO ONE Official Source Audit

Retrieved 23 August 2026 from the official `lz2jr.com` site. Exact URLs, sizes,
digests, versions, and watcher pins are machine-readable in `UPSTREAM.json`.

## Current documents

| Source | Version | SHA-256 | Decision |
|---|---:|---|---|
| V6 operating manual | 1.01A, Dec 2025 | `4f362fc988b033fce2b2b2c982a07be1d3076d48fd21aded7fad89969862fa3f` | Review only |
| V6 CAT manual | 1.03, FW 1.08+ | `6dd8aa96ab92ed7aa666dc1dac2847bc0158fa7c1526f86d04dc9ecfa819d78b` | Review only |
| Firmware release notes | 1.09, Aug 2026 | `5285a1a4bb36a3851730aeafb4b63974a3078f6e58d633f14dd7054bf8566852` | Factual review |
| Firmware release notes | 1.08, Jun 2026 | `2115c6473815a6639351a62a5bef23dbcf01dd3aace55c3d953b01ad83e26e58` | Factual review |
| Audio DSP filter option | 1.01 | `f998c80fcd505de2570202ec71419b41e4bf4917779880be0cd8af8e478a8665` | Review only |

The official firmware index and 21 August announcement establish 1.09 as current.
The 1.09 notes say MC/MR/MW were corrected, add EX access for CW memories, correct
frequency entry with RIT/XIT, and adjust mute timing. The code does not use firmware
binary content and the watcher refuses firmware/archive extensions.

## Legacy and module documents

The official manual index currently links operating manual 2.00A, operating manual
1.01b, ATU, NB 1.01A, VBF 1.2, and BPF160/60m documents. These were reviewed and
hashed. The legacy operating manual confirms a Kenwood-type protocol and menu 22
baud selection, but it does not define an exact series 5/5+ CAT command set. No legacy
CAT manual appeared in the official manual index or official WordPress media search.

The V6 operating material identifies optional NB, AF/DSP, ATU, transverter, speech
processor, RX antenna, and USB audio capabilities. Exact presence is accepted only
from a documented response/menu, operator confirmation, or USB descriptor evidence.

## Documentation conflicts and gaps

- CAT v1.03 is the current command authority. Its menu 3 TTL normal/sync description
  supersedes the older V6 operating manual text for firmware 1.08+.
- The V6 operating manual lists an extra `56000` baud value while the current CAT manual
  lists 9600, 19200, 38400, and 57600. The core uses the current CAT list.
- Neither current manual specifies data bits, parity, or stop bits. TTL open therefore
  requires supplied evidence rather than assuming 8-N-1.
- `IF` delegates its P1-P15 layout to TS-480. Without a complete RGO-specific layout in
  the reviewed official set, the core keeps the payload bounded and does not invent offsets.
- No exact CAT filter/bandwidth command or full optional-module absence protocol is given.
- The CAT manual's SN example uses an inconsistent prefix. Correlated SN replies are
  hashed regardless of prefix; the raw value is discarded.

## Redistribution

No explicit redistribution grant was located in the official site or reviewed PDFs.
The manuals were downloaded only into a temporary review directory and are not in Git.
This repository records factual metadata, short protocol facts, and independently written
implementation only.
