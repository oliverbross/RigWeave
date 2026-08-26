# RC1 Source Distribution

`scripts/prepare_rc1_distribution.py` creates a source archive directly from the exact Git tree, a per-file `SOURCE_MANIFEST.json`, SPDX 2.3 `SBOM.spdx.json`, `BUILD_MANIFEST.json` and `SHA256SUMS.txt`. The archive is named `RigWeave-source-<exact-sha>.tar.gz` and includes the complete tracked corresponding source, licences, notices, build scripts and vendored-source provenance.

The SBOM records RigWeave plus the single pinned Hamlib 4.7.2 authority, SDRoxide provenance record, `mfsk-core`, `tempo-sstv`, SGP4, ITUHFProp, CTY and band-plan snapshots. `NOTICE` and the detailed third-party audit remain authoritative for licence texts, modifications and upstream locations.

The upstream `mfsk-core` external corpus under `embedded-poc/assets/golden` is not vendored and is not silently downloaded. RC validation runs the full vendored library catalogue plus RigWeave's checked-in public-recording, FFI and golden contracts.

Binary artifact naming is exact-SHA based:

- `RigWeave-Android-arm64-v8a-<sha>.apk`
- `RigWeave-Android-four-ABI-<sha>.aab`
- `RigWeave-Windows-x64-portable-<sha>.zip`
- `RigWeave-Windows-x64-setup-<sha>.exe`
- `RigWeave-macOS-arm64-unsigned-<sha>.zip`

Artifacts are unsigned RC evidence. Creation does not publish or distribute them.
