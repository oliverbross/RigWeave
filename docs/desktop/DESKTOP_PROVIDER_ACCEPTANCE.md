# Desktop Provider Acceptance

Provider clients are foreground, bounded and cache last-good data. Credentials are resolved from Windows Credential Manager or macOS Keychain by alias and are never stored in config, databases, logs or support bundles.

| Provider class | Source acceptance | Live acceptance |
|---|---|---|
| Wavelog and Groups.io | Fake HTTP service, bounded response policy, alias-only authentication, outbox ambiguity/reconciliation | Pending authenticated tenant/account runs |
| Cluster, RBN, PSK Reporter and WSPR | Shared spot/evidence owners, bounded parsing and retained last-good state | Pending live-network runs |
| HamClock/weather/solar/news | Bounded HTTPS clients, content-type/size/cooldown policy | Pending provider-by-provider runs |
| POTA/SOTA/WWFF/IOTA and calendars | Cached catalogue/activity projections and explicit refresh | Pending accounts/rate-limit observation where applicable |
| Satellite/TLE | Bounded catalogue/TLE input and local SGP4 | Pending live catalogue refresh |

No permanent background polling is enabled. Provider absence or rejection is represented as unavailable/stale, never fabricated data.
