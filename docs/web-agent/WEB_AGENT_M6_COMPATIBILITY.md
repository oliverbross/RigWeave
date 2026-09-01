# Web Agent M6 compatibility

| Contract | Version |
|---|---:|
| Agent remote | 1.x |
| Observer | 1.0 |
| Media | 1.0 |
| Domain journal | 1 |
| Safe control | 1.1 |
| Workflow control | 1.2 |

The Agent accepts workflow major 1 and minor versions through 1.2. A future minor or any other major is refused. Unknown envelope fields are ignored only after required fields, bounds, role, capability, expiry and generation checks pass. M5 safe-control remains independently available.

The frozen Android parity reference remains `1e917fd2a0ec38a6b66eb9ab211d30ab48c331d3`; this branch does not change it.
