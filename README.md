# RigWeave

RigWeave is a radio-native portable operating cockpit that connects discovery, tuning, operating, logging, synchronisation, and progress without requiring fabricated state or permanent network access.

The current repository contains two native mobile clients—an iPad-focused SwiftUI client and an Android Jetpack Compose client—over a shared C++17 core. Android supports the existing Elecraft KX3/KX2 path plus a complete source-level FlexRadio LAN/SmartLink cockpit: VITA panafall/meters, multi-slice controls, PC audio and session-gated microphone/voice/CWX/MOX/TUNE paths. Build and Lenovo launch evidence pass; authenticated SmartLink, real FLEX-8400 VITA/audio and controlled RF remain physical-validation gates. Android also implements KX3 EQ Studio, SSB voice macros, Portable Chase, POTA Activate, Sync Hub and Progress Intelligence. SOTA live remains unavailable pending written programme API approval. FlexRadio is not claimed for Apple, and desktop, QMX and SOTA/WWFF activation remain planned work.

## Current implementation

| Layer | Current truth | Main paths |
|---|---|---|
| Shared core | KX3/KX2 CAT parsing and safety classes, ADIF, CTY, spot/DX analysis, operator intelligence, panadapter DSP, Wavelog retry policy, and bounded WSJT-X parsing behind a C ABI | core/include, core/portable, core/src |
| Apple | SwiftUI app, Objective-C++ bridge, base USBDriverKit KXUSB transport, local SQLite/ADIF, callbook, Wavelog, cluster/DX, and physical-I/Q panadapter | ios/RigWeave, ios/CP210xDriver |
| Android | Compose app, JNI/C ABI bridge, KX USB serial, Nexus/AetherSDR-attributed Flex LAN/SmartLink operating cockpit, local SQLite/ADIF, Sync Hub, Progress Intelligence, callbook, Wavelog, CW/SSB macros, KX3 EQ Studio and panadapter, Portable Chase, POTA Activate and DX/Neural DX | android/app/src/main, rust/rigweave-flex |
| Planned / gated | Physical FLEX-8400/SmartLink/VITA/audio/RF acceptance, SOTA live API approval, platform parity, SOTA/WWFF Activate, Qt/QML desktop, QMX and broader integrations | docs/ROADMAP.md |

The Apple Xcode targets are configured for device family 2 (iPad), deployment target iOS 17, and should not be described as proven iPhone support.

## Build

Shared core:

~~~sh
cmake -S core -B core/build-phase0 -DCMAKE_BUILD_TYPE=Debug
cmake --build core/build-phase0 --parallel
ctest --test-dir core/build-phase0 --output-on-failure
~~~

Android:

~~~sh
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
~~~

The Android build also requires stable Rust, `cargo-ndk`, and the four Android Rust targets documented in [the FlexRadio port guide](docs/FLEXRADIO_NEXUS_PORT_ANDROID.md). The authorised SmartLink OAuth client ID belongs only in ignored `flex-developer.properties` or the documented environment variable. Fixed endpoints and command forms follow FlexRadio's April 2026 SmartLink API reference.

The Android SDK path must be configured through ANDROID_HOME, ANDROID_SDK_ROOT, or an untracked local.properties. Do not accept SDK licences merely to turn an unavailable environment into a claimed pass.

Apple:

~~~sh
xcodebuild -project ios/RigWeave.xcodeproj -list
xcodebuild \
  -project ios/RigWeave.xcodeproj \
  -scheme RigWeave \
  -configuration Debug \
  -destination 'generic/platform=iOS' \
  -derivedDataPath ios/DerivedDataPhase0 \
  build
~~~

Do not change signing identities, profiles, entitlements, DriverKit identifiers, or bundle IDs to manufacture a build.

## Evidence status

| Evidence | Phase 0 result | What it proves |
|---|---|---|
| Shared core Debug build and CTest | PASS, 1/1 on 2026-08-17 UTC | Host compilation and focused shared-core tests |
| Android unit tests and Debug APK | PASS on 2026-08-17 UTC | Unit suite and all configured Android ABIs assemble |
| Generic iOS Debug build | PASS on 2026-08-17 UTC using existing profiles | Apple app and DriverKit targets compile, link, embed, and sign |
| Physical iPad, KXUSB, KX3, and I/Q | Historical repository evidence, not repeated in Phase 0 | See docs/COMPLETION_REPORT.md and docs/PANADAPTER_DESIGN.md |
| Physical Android KX3 EQ and USB audio | PASS WITH NOTES on 2026-08-17 UTC | Lenovo TB373FU, KXUSB, KX3 firmware 03.02, exact RX/TX write-readback-restore, and real 48 kHz mono capture/A-B; test input was too quiet for a credible acoustic recommendation |
| Wavelog authenticated workflow | UNVERIFIED | No usable API key/station-profile proof was available in the recorded pass |
| FlexRadio Android Phase 5 complete client | STOPPED at external physical acceptance | Rust/JVM/CTest, four ABI/JNI link, APK, install/launch and offline Lenovo cockpit pass; owner authentication, FLEX-8400, real VITA/audio and controlled RF were not exercised |

A successful build is not proof of USB enumeration, DriverKit activation, CAT semantics, audio orientation, remote authentication, service terms, or physical-radio operation.

## Documentation map

- [Product contract](PRODUCT.md)
- [Design contract](DESIGN.md)
- [Roadmap](docs/ROADMAP.md)
- [Architecture boundaries](docs/phase-0/ARCHITECTURE_BOUNDARIES.md)
- [Actual feature inventory](docs/phase-0/ACTUAL_FEATURE_INVENTORY.md)
- [Licensing and Nexus assessment](docs/phase-0/LICENSING_AND_NEXUS_ASSESSMENT.md)
- [Phase 0 audit](docs/phase-0/PHASE0_AUDIT.md)
- [Next authorised phase gate](docs/phase-0/NEXT_AUTHORISED_PHASE.md)
- [Android Phase 1 integration closure](docs/phase-1/ANDROID_STUDIO_INTEGRATION_CLOSURE.md)
- [Android POTA Activate](docs/POTA_ACTIVATE_ANDROID.md)
- [Android FlexRadio Nexus port](docs/FLEXRADIO_NEXUS_PORT_ANDROID.md)
- [Android complete FlexRadio client](docs/FLEXRADIO_COMPLETE_ANDROID.md)
- [Next Phase 1 candidate](docs/phase-1/NEXT_AUTHORISED_PHASE.md)

## Licence

RigWeave is licensed under GPL-3.0-only. See [COPYING](COPYING) for the complete licence and [NOTICE](NOTICE) for the notice policy.

GPLv3 permits charging for binaries, support, and services; it does not require zero-price distribution. Every distributed covered binary must map to an immutable source commit/tag and be accompanied by, or provide equivalent access to, complete corresponding source, build instructions, dependency manifests/lock data, applicable patches, and notices.

Nexus was inspected as an external GPLv3 upstream during Phase 0. Android Phase 5A now incorporates only the attributed Flex discovery and receive-control source recorded in [`rust/rigweave-flex/UPSTREAM.md`](rust/rigweave-flex/UPSTREAM.md); it imports no Nexus UI, DAX/TX orchestration or unrelated workspace component.

## Android implementation details

Install the built debug APK on an emulator or connected Android device:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.rigweave.mobile/.MainActivity
```

An emulator can validate navigation and local logging, but cannot prove USB serial or radio operation. The Android app uses `usb-serial-for-android` for PL2303, CP210x, FTDI, CH34x, and CDC-ACM adapters where supported by that library. It opens the selected adapter at 38,400 baud and exposes frequency, mode, and raw CAT controls without simulation.

The Android source also binds the full portable feature engine through JNI and supplies the KX3-style live CAT deck, local ADIF journal/import/export, Wavelog discovery/upload/full-log cache, six graphical DX views, real TCP DX-cluster, NOAA, and a first-class production panadapter. The panadapter selects and proves an external stereo route, uses its own native DSP context and capture lifecycle, follows fresh CAT frequency truth, and fails closed rather than converting mono audio into a spectrum claim. Android SDK licences were accepted with the owner's explicit approval and NDK `28.2.13676358` is installed under the SDK used by Gradle.

Android also includes six SSB voice-macro slots for exact USB/LSB operation. Recordings remain in private tablet storage, preview is verified against the built-in speaker, and transmission uses an explicitly selected DigiRig USB output with left-channel speech/right-channel silence. Voice TX must acquire the shared `VOICE_TX` audio lease before route preparation or CAT PTT; failed ownership produces zero `TX;` commands. PTT is then owned only by verified Elecraft CAT (`TQ0 -> TX -> TQ1 -> audio -> RX -> TQ0`); RTS/DTR are kept inactive. Multiple CAT or USB-audio candidates require explicit selection. See [`docs/VOICE_MACROS_ANDROID.md`](docs/VOICE_MACROS_ANDROID.md) for setup, privacy limits, calibration, and the operator-controlled dummy-load checklist.

Android EQ Studio is a dedicated responsive KX3 calibration workspace. It reads exact RX/TX menu values, separates verified radio state from local drafts and profiles, records one transient 10–15 second physical-input clip, supports raw-reference or hardware-baseline delta preview, and applies only after `TQ0`, menu/context, conflict, and exact readback checks. Expanded layouts expose EQ in the rail; compact layouts retain the six destinations and open it from Radio or Settings → Audio. See [`docs/EQ_STUDIO.md`](docs/EQ_STUDIO.md).

## Panadapter architecture

The mobile panadapter consumes physical stereo I/Q only. Its shared FFT path now uses independent DC removal, coherent-gain-normalized complex magnitudes, Blackman-Harris windowing, FFT shift, and asymmetric dB-domain attack/release smoothing. It does not infer an I/Q calibration transform from each live frame.

Both native clients provide spectrum/waterfall instrumentation, while implementation and evidence remain platform-specific. Android adds resizable/single-pane layouts, a circular bitmap waterfall, honest optional visual center masking, true decimation zoom, explicit marker QSY/confirmed undo, route diagnostics, bounded recording/replay, and device/rate-bound I/Q calibration. See [`docs/PANADAPTER_DESIGN.md`](docs/PANADAPTER_DESIGN.md) and [`docs/panadapter/PANADAPTER_OPERATOR_GUIDE.md`](docs/panadapter/PANADAPTER_OPERATOR_GUIDE.md).

Android software/device integration is fail-closed. New or missing panadapter settings default to 48 kHz while explicit saved 48/96 kHz choices remain unchanged. Physical KX3 quadrature-I/Q RF acceptance remains deferred; the retained mirror-dominant evidence is not a successful RF claim.

## Current physical status

- Physical iPad: iPad Pro 11-inch (4th generation), `iPad14,4`, iPadOS 26.6.1, Developer Mode enabled and connected through CoreDevice.
- Signed build 38 and its embedded PL2303GC/KXUSB DriverKit extension build, install, launch, and verify on the physical iPad.
- Portable feature-core tests and the signed iPad device build pass with the cluster, DX, Wavelog, callbook, and panadapter code included.
- Physical CAT passes through the enabled DriverKit extension and real Elecraft KXUSB (`067B:23A3`): build 38 opened the PL2303GC at 38,400 8N1, identified the connected radio as KX3, and read VFO A at 21.1366 MHz. A physical UI test connected in Settings, navigated through Home and Radio, returned to Settings, and verified that CAT and frequency stayed live.
- Physical ICUSBAUDIO2D stereo I/Q passes at 48 kHz: more than 1.2 million live frames reached the shared 1,024-bin spectrum pipeline during the build-30 soak. The DSP now matches Tab5's proven analogue I/Q imbalance correction and complex-tone image-rejection test; every PCM frame is processed while SwiftUI presentation is bounded to about 9 FPS.
- The saved `OM0RX-6` profile was exercised against `cluster.om0rx.com:7300` on the physical iPad: login, `sh/dx 50`, parsing, shared-core snapshot generation, and populated Spots UI passed with real records. Wavelog authentication still requires a configured API key and station profile.
- Android voice-macro JVM tests and debug APK build are software evidence only. Connected-tablet launch and physical radio checks, plus every operator-controlled dummy-load/RF acceptance item, must be reported separately and must not be inferred from the build.
- Android KX3 EQ Studio was physically exercised on a Lenovo `TB373FU` with real KXUSB and KX3 MCU firmware `03.02`: RX CW and TX normal-SSB curves were changed by one dB, read back exactly, and restored exactly without any transmission-capable command. Real `USB Advanced Audio Device` captures and A/B playback ran at 48 kHz mono, but the source was too quiet for a credible acoustic recommendation. Split/ESSB hardware buckets, physical conflict injection, compact-phone validation, and KX2 writes remain unverified or deferred. See [`docs/EQ_STUDIO.md`](docs/EQ_STUDIO.md).

Public Apple distribution presents an unresolved GPLv3/platform risk. No App Store submission is authorised; qualified legal review and/or suitable additional permission is required before that distribution path is claimed compatible.
