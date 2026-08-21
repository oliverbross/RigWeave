# Android app size and module policy

RigWeave remains one coherent product. Neural DX, HamClock, Wavelog, and Groups.io are not separate user-facing applications or dynamic feature modules.

Internal gates:

- universal debug APK: at most 130 MB;
- debug AAB compressed estimate: at most 150 MB;
- the Neural/HamClock empirical-outlook phase adds no runtime, executable model, downloaded model or data asset; its APK delta target is at most 5 MB and debug AAB phase target is at most 60 MB total.
- no new optional asset family above 10 MB without a manifest and decision;
- no ITU/P.533 implementation source or coefficient payload.

The deterministic audit is `scripts/audit_android_package_size.py`. It reports archive totals, the largest 40 entries, dex/resources/assets/native-library totals, ABI totals, repeated basenames and identical payloads, files above 1 MB, reference APK deltas, and the ITU/P.533 payload scan.

Size remediation order is duplicate/obsolete artefact cleanup, preserved App Bundle splitting, runtime caches outside the package, and—only if useful—an optional arm64 physical-test APK property. Dynamic features are justified only if the consolidated base remains above 150 MB after cleanup or a future optional content family exceeds 20 MB.

The final hardening artifacts remain within the same single-module policy: debug APK 114,649,022 bytes (`7402417fe8533b93daf67b714bf22279ca3367986ab8340a7479dcd6d8a1abe1`) and debug AAB 51,623,548 bytes (`7f56e9416ea2a54ab00c62f3ac1d3637c46e53bfcb68c6cf6778790dfe8b1376`). The audit reports a +4,828,110-byte APK delta against the combined-integration reference, below the +5 MiB phase target, and passes the ITU/P.533 payload scan. Detailed evidence is in `NEURAL_OUTLOOK_FINAL_HARDENING.md`; the earlier consolidation record remains in `UNIFIED_NEURAL_HAMCLOCK_CONSOLIDATION.md`.

## Nexus Digi v2 package evidence

The Nexus Digi completion remains in the same base module and adds no bundled
model, upstream desktop runtime, WebView asset family, Fortran/FFTW payload or
dynamic feature. Final debug artifacts:

- universal APK: 110,437,257 bytes,
  SHA-256 `41887ea94b24fc795db4038f9dd11618cc46ff9576b1354820da4afc3bbbeaba`;
- AAB: 51,797,260 bytes,
  SHA-256 `fbb02d4b7ae9cae68217a3bff828c38c7a14574097da763e4c73c275d7ad9940`.

The deterministic audit reports APK delta +616,345 bytes versus the combined
integration reference and +330,767 bytes versus Finish-Line. Both artifacts
pass the ITU/P.533 payload scan and remain below the existing package ceilings.
