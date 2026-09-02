# M9 device identity and keys

Android request identity is non-exportable Keystore P-256; Apple uses Secure Enclave/Keychain P-256. Exportable libsodium box and Sync Space keys are stored only under platform secure-storage protection. Event bodies use XChaCha20-Poly1305-IETF and 24-byte nonces; Sync Space Keys use sealed per-device X25519 envelopes. Revocation rotates key versions.
