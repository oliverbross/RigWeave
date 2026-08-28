# iOS/iPadOS RC1 Build and Signing Boundary

The published Apple outputs are a simulator ZIP and an unsigned XCArchive for iPhone/iPad. They prove source/build parity but are not an IPA and cannot establish signed-device or App Store acceptance. Pairing identity uses Keychain; remote TLS uses a stored certificate pin. Production signing, provisioning, device installation, and store submission are explicitly outside this RC.

