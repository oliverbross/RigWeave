# RigWeave

RigWeave is a radio-native portable operating cockpit that connects discovery, tuning, operating, logging, synchronisation, and progress without requiring fabricated state or permanent network access.

The current repository contains two native mobile clients—an iPad-focused SwiftUI client and an Android Jetpack Compose client—over a shared C++17 core. Elecraft KX3/KX2 is the current radio family. Desktop, FlexRadio, QMX, portable-programme workflows, RX/TX EQ Studio, and voice macros are planned work, not shipped capability.

## Current implementation

| Layer | Current truth | Main paths |
|---|---|---|
| Shared core | KX3/KX2 CAT parsing and safety classes, ADIF, CTY, spot/DX analysis, operator intelligence, panadapter DSP, Wavelog retry policy, and bounded WSJT-X parsing behind a C ABI | core/include, core/portable, core/src |
| Apple | SwiftUI app, Objective-C++ bridge, base USBDriverKit KXUSB transport, local SQLite/ADIF, callbook, Wavelog, cluster/DX, and physical-I/Q panadapter | ios/RigWeave, ios/CP210xDriver |
| Android | Compose app, JNI bridge, USB serial, local SQLite/ADIF, callbook, Wavelog, CW macros, DX/Neural DX surfaces, MapLibre maps, audio monitoring and spectrum paths | android/app/src/main |
| Planned | KX3/KX2 Studio, Portable Chase/Activate, Sync and Progress, FlexRadio SmartLink, Qt/QML desktop, then QMX and broader integrations | docs/ROADMAP.md |

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
| Physical Android radio/audio | UNVERIFIED | No current repository evidence of a physical Android acceptance pass |
| Wavelog authenticated workflow | UNVERIFIED | No usable API key/station-profile proof was available in the recorded pass |
| FlexRadio, desktop, QMX, portable-programme operation | ABSENT/PLANNED | No current implementation or physical proof |

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

## Licence

RigWeave is licensed under GPL-3.0-only. See [COPYING](COPYING) for the complete licence and [NOTICE](NOTICE) for the notice policy.

GPLv3 permits charging for binaries, support, and services; it does not require zero-price distribution. Every distributed covered binary must map to an immutable source commit/tag and be accompanied by, or provide equivalent access to, complete corresponding source, build instructions, dependency manifests/lock data, applicable patches, and notices.

Nexus was inspected as an external GPLv3 upstream/reference during Phase 0. No Nexus source, binary, dependency, submodule, or derived implementation is included by this work.

Public Apple distribution presents an unresolved GPLv3/platform risk. No App Store submission is authorised; qualified legal review and/or suitable additional permission is required before that distribution path is claimed compatible.
