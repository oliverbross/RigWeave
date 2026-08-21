# Android app size and module policy

RigWeave remains one coherent product. Neural DX, HamClock, Wavelog, and Groups.io are not separate user-facing applications or dynamic feature modules.

Internal gates:

- universal debug APK: at most 130 MB;
- debug AAB compressed estimate: at most 150 MB;
- no new optional asset family above 10 MB without a manifest and decision;
- no ITU/P.533 implementation source or coefficient payload.

The deterministic audit is `scripts/audit_android_package_size.py`. It reports archive totals, the largest 40 entries, dex/resources/assets/native-library totals, ABI totals, repeated basenames and identical payloads, files above 1 MB, reference APK deltas, and the ITU/P.533 payload scan.

Size remediation order is duplicate/obsolete artefact cleanup, preserved App Bundle splitting, runtime caches outside the package, and—only if useful—an optional arm64 physical-test APK property. Dynamic features are justified only if the consolidated base remains above 150 MB after cleanup or a future optional content family exceeds 20 MB.

The final measured decision and hashes are recorded in `UNIFIED_NEURAL_HAMCLOCK_CONSOLIDATION.md`.

