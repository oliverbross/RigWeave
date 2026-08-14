# RigWeave Mobile

Native iPadOS and Android clients for direct Elecraft KX3/KX2 CAT control. The apps share a small C++17 core for bounded CAT parsing, radio state, deterministic QSO identity, and ADIF output. They contain no demo or simulated radio state.

## iPadOS

Requirements:

- Xcode 26.6 or newer;
- Apple Development team `4WCMQ4U946`;
- installed profile `RigWeave iPad Development` for `app.rigweave.mobile`;
- installed profile `RigWeave CP210x Driver Development` for `app.rigweave.mobile.CP210xDriver`;
- supported M-series iPad with Developer Mode enabled;
- CP2102 adapter (`VID 10C4`, `PID EA60`) and KX3/KX2 configured for 38,400 baud, 8N1.

Build the signed iPad application:

```sh
xcodebuild \
  -project ios/RigWeave.xcodeproj \
  -scheme RigWeave \
  -configuration Debug \
  -destination 'generic/platform=iOS' \
  -derivedDataPath ios/DerivedDataSigned \
  clean build
```

Install and launch on a connected, unlocked iPad:

```sh
xcrun devicectl list devices
xcrun devicectl device install app \
  --device '<COREDEVICE-IDENTIFIER>' \
  ios/DerivedDataSigned/Build/Products/Debug-iphoneos/RigWeave.app
xcrun devicectl device process launch \
  --device '<COREDEVICE-IDENTIFIER>' \
  app.rigweave.mobile
```

The host app embeds the signed DriverKit extension at `SystemExtensions/CP210xDriver.dext`. Connection begins only after tapping **Connect**, then the app polls `ID;`, `FA;`, `MD;`, `IF;`, and `TQ;`. Frequency, mode, and raw CAT controls pass commands to the real radio; there is no simulated fallback.

## Android

Requirements:

- JDK 17 or newer;
- Android SDK 36;
- NDK `28.2.13676358`;
- accepted Android SDK/NDK licences.

Build:

```sh
cd android
./gradlew :app:assembleDebug
```

Install on an emulator or connected Android device:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.rigweave.mobile/.MainActivity
```

An emulator can validate navigation and local logging, but cannot prove USB serial or radio operation. The Android app uses `usb-serial-for-android` for PL2303, CP210x, FTDI, CH34x, and CDC-ACM adapters where supported by that library. It opens the selected adapter at 38,400 baud and exposes frequency, mode, and raw CAT controls without simulation.

## Shared core tests

```sh
cmake -S core -B core/build
cmake --build core/build
core/build/rigweave_core_tests
```

## Current physical status

- iPad target detected previously: iPad Pro 11-inch (4th generation), `iPad14,4`, iPadOS 26.6.1, Developer Mode enabled.
- The signed iPad app and embedded CP210x DriverKit extension build and verify locally.
- Physical install, DriverKit activation, USB enumeration, and live KX3/KX2 CAT responses remain unproven until the iPad is connected and unlocked.
- No Android hardware or emulator proof has been completed in this repository.

