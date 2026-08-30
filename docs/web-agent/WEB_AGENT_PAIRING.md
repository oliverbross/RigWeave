# Web Agent pairing

`--pairing-offer` returns public station identity, endpoint, certificate SHA-256 pin, short-lived nonce, and OBSERVER default role. `--hub-identity` returns public Hub observer metadata and a vault alias. `--hub-sign` signs one bounded station-prefixed challenge inside the configured vault. `--approve-observer` consumes one pending offer as OBSERVER.

Production uses `SystemCredentialVault`. Debug/no-radio uses `FakeCredentialVault` within the running Agent and therefore requires re-pairing after Agent restart. Revocation closes the device authority and associated sessions. No command exports private key material.
