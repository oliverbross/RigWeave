# Signing and Distribution Runbook

RC1 outputs are unsigned evidence only. This runbook requires separate owner authority and release credentials.

1. Select the exact approved SHA and verify all source/binary digests.
2. Build in the approved clean signing environment without changing identifiers or embedded RC metadata unexpectedly.
3. Sign Android APK/AAB, Windows installer/binaries and macOS app/archive with the approved identities; notarize macOS where required.
4. Verify signatures, entitlements, package IDs, minimum OS/ABI set and install/upgrade behavior on disposable devices.
5. Recreate the signed digest ledger and attach corresponding source, SBOM, notices and manifests.
6. Obtain final visual, authenticated-service and hardware acceptance sign-off.
7. Publish only to the explicitly approved channels; record immutable URLs, versions and rollback procedure.

Never expose private keys or credential values in logs, archives, support bundles or CI artifacts. A failed or ambiguous signature check stops distribution.
