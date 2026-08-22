# Initial contest rule-pack matrix

| Variant | Exchange | Dupe | Points | Multipliers | Status |
|---|---|---|---|---|---|
| CQ WW CW / SSB | RST + CQ zone | per band | country/continent, including NA exception and valid zero-point same-country QSO | CQ zone + DXCC per band | implemented; 2026 edition confirmation `REVIEW_REQUIRED` |
| CQ WPX CW / SSB | RST + serial | per band | band-weighted country/continent | prefix | implemented, 2026 |
| ARRL DX CW / SSB | W/VE sends state/province; DX sends power | per band | valid only across W/VE/DX sides, 3 points | DXCC for W/VE entrants; state/province for DX entrants | implemented |
| IARU HF | RST + ITU zone or HQ/member abbreviation | per band/mode | same zone 1, same continent 3, different continent 5 | ITU zone + HQ/member society | implemented |
| ARRL Field Day | class + ARRL/RAC section or DX | per band/mode | phone 1; CW/digital 2 | section intelligence; official score has power/bonus claim inputs | implemented with explicit bonus boundary |
| CQ 160 CW / SSB | RST + state/province or CQ zone | once per contest | same country 2, same continent 5, different continent 10 | state/province + DXCC per band | implemented |
| Oceania DX CW / SSB | RST + serial | per band | 160/80/40/20/15/10 = 20/10/5/1/2/3 when either side is Oceania | prefix per band | implemented; 2026 PDF resolution `REVIEW_REQUIRED` |
| General DX/Serial | RST + serial | per band/mode | 1 | none | non-award fallback |

Every pack carries official-source metadata/digest and exchange, point, multiplier, dupe and export golden vector IDs.

## Explicitly deferred (`NOT_IMPLEMENTED`)

- WAE QTC traffic.
- Sweepstakes precedence/check/section workflows.
- Rover/mobile multi-location scoring.
- VHF/UHF distance/grid families.
- Multi-single ten-minute enforcement and full Multi-Multi coordination/interlock.
- Contest-specific online score submission.
- RTTY-specific parser/rule semantics beyond the typed generic architecture.
- Root navigation/calendar/configuration-recovery wiring, Apple and desktop contesting.
