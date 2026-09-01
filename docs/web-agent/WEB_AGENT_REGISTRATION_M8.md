# Web Agent registration M8

1. Generate a dedicated P-256 key in the Station Agent context and store the private PEM under a platform-vault alias.
2. Register only the public key, public-key ID, Agent registration ID, and station claim with Hosted.
3. Start `rigweave-stationd` with the five `--relay-*` options. Partial configuration fails closed.
4. Hosted sends a fresh 32–1024 byte challenge. The Agent signs it with SHA-256/ECDSA and echoes the challenge, station ID, registration ID, key ID, and current generation.
5. Hosted verifies the registered public key before marking the Agent presence live.

Rotation creates a new vault alias and public-key registration before removing the old alias. Revocation is effective at Hosted immediately and the Agent must reconnect with a non-revoked registration. Recovery never exports the private key.
