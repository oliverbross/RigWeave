# Intelligent Band Maps integrated input contract v1

This document freezes read-only inputs for the later Band Maps task. It does not implement a Band
Map, scorer, provider connection, radio action or transmit action.

## Existing intelligence owner

Band Maps must consume existing bounded immutable snapshots for cluster/RBN/PSK/personal WSPR,
portable activity, worked/confirmed/Needs, Band Health, Neural current opportunities, Empirical
Outlook, watchlist and `OperatingContextSnapshot`. It must not create another client or cache.

## Contest and N1MM

Stable types:

- `ContestReadOnlyPort`
- `ContestReadOnlySnapshot`
- `ContestOpportunityEvaluator`
- `ContestOpportunityState`
- `ContestClaimSnapshot`

The opportunity result supplies valid band/mode truth, dupe state, new/worked/unknown multiplier
types, expected exchange/source and trusted peer claim. It is derived by the Contest authority and
must not be recalculated in Band Maps. N1MM peer/claim state is context only and cannot tune, key,
log, start Keyer/Digi/Chaser or change Contest state.

## DX Chaser

Stable types:

- `DxChaserReadOnlyPort`
- `DxChaserReadOnlySnapshot`
- `DxChaserCandidateSnapshot`
- `DxChaserContestSnapshot`

The bounded snapshot exposes eligibility, priority tier, transparent reasons, target/engaged call,
cooldowns, current evidence, empirical outlook label, explicit safety truth and Contest context. Band
Maps may display these values but must not call the Chaser engine or database directly.

## Keyer

Stable types:

- `KeyerDispatchPort` (not callable by Band Maps)
- `KeyerQueueSnapshot`
- `KeyerAvailability`
- active Contest role/profile from `ContestReadOnlySnapshot` plus the Keyer profile owner

Band Maps may display queue/availability/context. It cannot dispatch a message, arm Keyer or start
repeat CQ.

## Actions

All future actions route through `WorkspaceActionRouter` using exact destinations such as `CONTEST`,
`DX_CHASER`, Digi, Logbook and DX details, or through the existing receive-review authority. A
receive review may prepare a receive frequency for explicit confirmation only. Digi preparation,
watchlist mutation and logging remain in their existing reviewed owners. No Band Map action may key
PTT, start TUNE, change TX frequency, enable Digi TX, send a macro, start a chase or log a QSO.

Snapshots are immutable, bounded and generation-stamped. Missing, stale or unknown values stay
unknown; consumers must never invent a dupe, multiplier, need, probability, peer claim or RF result.
