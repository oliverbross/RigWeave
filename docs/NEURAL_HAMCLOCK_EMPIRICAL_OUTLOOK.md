# RigWeave Empirical Outlook v1

## Product truth

This is an empirical RF outlook built from RigWeave-owned observations. It is not P.533, VOACAP, a neural network, an ionosonde, an exact future callsign prediction, or transmit authority. Output uses `INSUFFICIENT_EVIDENCE`, `QUIET`, `BUILDING`, `FAVOURABLE`, `STRONG`, and `DEGRADED`, plus `LOW`, `MEDIUM`, or `HIGH` confidence. A percentage is withheld until calibration gates pass.

Current callsigns remain current observations. Future candidates are separately attributed as `CURRENTLY OBSERVED`, `SCHEDULED`, `WATCHLIST`, `NEEDED`, or `RECENT PATTERN`; a scheduled candidate is not a predicted transmission.

## Evidence and storage

`neural-dx.sqlite` schema 4 preserves the 90-day `spot` journal and adds `evidence_bucket`, `outlook_prediction`, `outlook_calibration`, and `outlook_meta`. Five-minute buckets are separated by station profile, station callsign, normalized grid, band, mode family, 6 × 12 region and provider identity. They retain counts, bounded callsign hashes, receiver/skimmer diversity, SNR/distance aggregates, and source freshness—not raw responses, credentials, comments, QSO payloads, or arbitrary JSON.

Evidence and prediction retention is 180 days, calibration is model-version scoped, and the database soft target is 50 MB. Startup does not vacuum or synchronously rebuild history. The v3→v4 migration creates tables/indexes transactionally, records a cutoff, and resumes cluster-only journal backfill by `rowid` in cancellable batches of at most 1,000. Live aggregation uses replace-by-bucket semantics, so repeated snapshots do not inflate counts.

## Time-matched baseline and model

The baseline uses the target UTC quarter-hour with ±30-minute tolerance over a bounded 56-day history. It requires at least eight matched buckets and remains station-, band- and region-scoped. Evidence retains major mode-family keys; the global operator outlook deliberately aggregates those families where a mode-specific sample would be too sparse. Current World anomalies and future windows query `evidence_bucket`; neither repeatedly scans raw spot rows nor compares a target time with an all-day mean.

The versioned score is bounded 0–100. Coefficients live in `RigWeaveEmpiricalOutlookV1`: baseline propensity 20, current/matched-baseline support 25, trend 15, source/call/receiver diversity 20, distance/path diversity 10, environmental context −10…+10, and explicit freshness/degradation penalties. QSO aggregates are personal context only and never contribute live support. Calendar, watchlist and Needs rank value only after the RF outlook.

All 16 canonical bands are retained. 4 m/2 m/70 cm require current and historical terrestrial evidence; 23 cm/3 cm require stronger multi-source local support and cannot be inferred from solar context alone. Output windows are 30, 60 and 120 minutes. World/map output is capped at 72 cells.

## Verification and calibration

Ended predictions verify in foreground, after ingestion, in batches of at most 100. `HIT` requires two unique calls in one valid source or one bounded callsign hash in two sources. `MISS` requires valid source coverage without a hit. Provider outage/degradation sufficient to make absence meaningless is `UNVERIFIABLE`, never a miss.

Scores calibrate by decile, window and band family. A displayed hit rate requires at least 40 verified predictions for that window/band family and 15 in the score bin. Beforeward the UI says `Calibration collecting · N verified`; afterward it says `Empirical hit rate · XX% · N samples`. Laplace smoothing is applied. Model versions never mix bins.

## Lifecycle and safety

One controller coalesces immutable snapshots off-main: latest snapshot wins, writes do not overlap, ingestion is limited to once per minute, normal recomputation to once per five minutes, and station/window/source changes or manual refresh may recompute immediately. A background transition requests one bounded flush; there is no WorkManager or permanent service. Close cancels pending work, and stale generations cannot overwrite a newer snapshot.

No outlook object contains a CAT frequency or command. The established direct Neural CAT guard above 54 MHz, receive-review flow, and prohibitions on automatic PTT/TUNE/frequency change/macro/spot/log actions remain unchanged.
