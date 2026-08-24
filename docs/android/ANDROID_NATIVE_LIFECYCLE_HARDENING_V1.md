# Android native lifecycle hardening v1

Date: 2026-08-25  
Branch: `fix/android-native-lifecycle-hardening-v1`  
Implementation anchor: `25bc191b868b75facc27bc086ba1e8bd42003d8a`  
Sweep 3 source: `8582c0250188f62d683e10c156e7261a07b3dd6c`

## Outcome

The tablet-observed FeatureController use-after-free was a real ownership defect: a delayed CTY load could call a destroyed feature context. The repair is now systematic rather than feature-local. Native pointer owners use a checked lease, retirement clears visibility before destruction, close is idempotent, and late asynchronous publication is generation-gated.

## Repaired findings

- Feature CTY, worked-log, cluster, solar and snapshot work now routes through `FeatureNativeSession`; no controller code retains or escapes its raw pointer.
- Digi configuration replacement, Flex reader shutdown, Panadapter capture/replay, embedded Hamlib radio and embedded Hamlib rotator all use checked handle ownership.
- Satellite provider/calculation/selection generations prevent result publication after observer, selection or owner change.
- Audio monitor stop retires worker callbacks before releasing `AudioRecord` and `AudioTrack`; device callback unregister is once-only.
- Seven MapLibre workspaces retire style/camera/marker callbacks and remove listeners on disposal.
- Both secure WebView paths stop loading, detach clients/listeners, remove the view from its parent and destroy once while preserving exact-host JavaScript policy.
- Synchronous USB adapters no longer perform `runBlocking` disconnect during lifecycle close. Their managed owner completes the suspend disconnect; Digi close performs bounded receive cleanup without blocking the caller.
- Schema-16 close/reopen preserves canonical QSO, projection relationship and settings metadata, and repairs a deliberately missing projection without recreate/downgrade.
- JNI entry points reject zero handles and malformed/bounded arrays, dimensions and sample rates with neutral return values.

## Unchanged safe authorities

Canonical QSO mutation remains singular. Radio and rotator restore remain disconnected and inert. Unknown capability remains unknown. No automatic CAT, PTT, TUNE, RF transmission or rotator movement was added. Physical QMX, RGO ONE, Hamlib model, audio route and rotator behavior remain live-evidence items.

## Local validation

- Android: 711 JVM tests, four-ABI debug bundle, instrumentation source/test APK, lint, and arm64 debug APK passed.
- Rust: 98 passed, one intentionally ignored.
- Native Debug CTest: 3/3 passed.
- Native ASan+UBSan CTest: 3/3 passed.
- Apple: unsigned generic iOS Simulator and generic iOS builds passed.
- Release contract audit: PASS.
- Package: 58,426,676-byte arm64 APK and 55,739,195-byte four-ABI AAB; prohibited payload, private evidence, rigctl/rigctld and duplicate libc++ checks passed.
- Watchers: Wavelog, Hamlib, MSHV, Neural DX and RGO ONE returned no source blocker; Nexus, OpenHamClock, QMX and rotator sources returned advisory review-required/unavailable status. No upstream source was absorbed.

Exact-SHA hosted evidence and protected-tablet evidence are recorded separately because source/build results do not prove device, authenticated service, audio, CAT/PTT/TUNE, RF or rotator behavior.

