# Cluster and Band Map data flow

The production chain is singular:

`FeatureController socket → native cluster parser/repository → liveSpots → BandMapSourceAdapters.cluster → BandMapController → Radio / Band Maps / Contest`.

The Sweep 3 root cause was lifecycle ownership: observation submission occurred inside `BandMapScreen`, so Radio and Contest saw an empty controller until that destination had been composed. Submission now occurs in the always-composed application `Screen` layer. `BandMapScreen`, `CompactRadioBandMap`, and Contest are read-only consumers.

Cluster diagnostics expose received, parsed, rejected/unrecognised and accepted counts without raw lines. Band Map diagnostics expose observation, repository, source-filter, band/mode-filter, intelligence-filter, displayed and generation counts. Empty state distinguishes disconnected/no observations, connected/no spots, filtered/unsupported and degraded sources.

`SH/DX <count>` is sent only after explicit connected operator action, is bounded to 1–500 (UI presets 10/20/50/100/200), permits one outstanding request, has an idle completion/timeout state, and enters the same parser/repository without clearing live rows.
