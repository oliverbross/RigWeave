# M9 iOS/iPadOS sync

Apple SQLite user version 1 creates normalized sync metadata transactionally. `QSOStore` enqueues linked creates and undo tombstones atomically. Swift-Sodium implements XChaCha20-Poly1305 and sealed boxes; the request identity is Secure Enclave-backed on device with a Keychain-backed simulator fallback. No IPA, TestFlight, or signed-device claim is made.
