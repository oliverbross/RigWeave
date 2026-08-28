# Pairing and Roles

1. Start the explicitly enabled station service and create a two-minute pairing offer.
2. Transfer the JSON/QR over an operator-trusted channel.
3. Android validates expiry and the exact SHA-256 certificate pin, creates/uses its Android Keystore P-256 identity, and signs the offer challenge.
4. The station records a pending public key only after signature validation.
5. A local station operator approves OBSERVER, OPERATOR, or ADMIN.
6. Later connections sign a fresh authentication challenge. Revocation closes active sessions and clears leases.

OBSERVER receives permitted read-only projections/media. OPERATOR may request writer/TX/rotator leases, subject to station policy and physical authority. ADMIN adds local administration eligibility; it does not bypass leases, interlocks, or physical acceptance.

Pairing secrets/private keys are never exported. Forgetting an Android profile removes connection metadata but deliberately does not delete the shared Keystore identity. Restore is disconnected and disarmed.
