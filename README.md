# RigWeave Mobile

Native iPadOS and Android clients for direct Elecraft KX3/KX2 operation. The apps share a C++17 engine for CAT, ADIF, cluster spots, CTY resolution, worked-status intelligence, DX analysis, panadapter DSP, Wavelog retry policy, and WSJT-X decoding. They contain no demo or simulated radio state.

## iPadOS

Requirements:

- Xcode 26.6 or newer;
- Apple Development team `4WCMQ4U946`;
- installed profile `RigWeave iPad Development` for `app.rigweave.mobile`;
- installed DriverKit App Development profile `RigWeave CP210x DriverKit Development` for `app.rigweave.mobile.CP210xDriver`;
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

The host app embeds the signed DriverKit extension at `SystemExtensions/CP210xDriver.dext`. Connection begins only after tapping **Connect**, then the app polls the real KX3 identity, dual-VFO, mode, TX, meter, gain, bandwidth, power, preamp, attenuator, RIT/XIT, and split state. The KX3-inspired control deck and every CAT button operate on those responses; there is no simulated fallback.

The native iPad app also includes:

- a real TCP DX-cluster connection with callsign login, watchlist ranking, CTY enrichment, band activity, and one-tap CAT tuning;
- live NOAA SFI/Kp retrieval;
- real UDP WSJT-X datagram reception and parsing;
- physical AVAudioSession input capture feeding the shared 1,024-bin panadapter DSP;
- local SQLite QSO logging, searchable journal, and whole-log ADIF export;
- Wavelog station discovery, station selection, cursor-based full remote-log caching, and a durable upload queue with Keychain credentials, acknowledgement, quarantine, and bounded retry/backoff;
- the Tab5-derived graphical DX suite: live opportunities, smart ranking, bandmap, 12-bucket band pulse, world heat grid with regional activity, and watchlist activity;
- authenticated QRZ or HamQTH callbook lookup with credentials stored in the device Keychain.

Each service remains explicitly offline until its real connection, credentials, or physical input is available. No fixture spots, generated spectrum, fake radio state, or automatic test QSO is injected.

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

The Android source also binds the full portable feature engine through JNI and supplies the same KX3-style live CAT deck, local ADIF journal/export, Wavelog discovery/upload/full-log cache, six graphical DX views, real TCP DX-cluster, NOAA, UDP WSJT-X, and `AudioRecord` physical-input panadapter paths with matching Compose screens. Android SDK licences were accepted with the owner's explicit approval and NDK `28.2.13676358` is installed under the SDK used by Gradle.

## Panadapter architecture

The mobile panadapter consumes physical stereo I/Q only. Its shared FFT path now uses independent DC removal, coherent-gain-normalized complex magnitudes, Blackman-Harris windowing, FFT shift, and asymmetric dB-domain attack/release smoothing. It does not infer an I/Q calibration transform from each live frame.

Both native clients render a 41/59 spectrum/waterfall split with a centered VFO axis, scrolling newest-first history, live trimmed-mean noise-floor tracking, adjustable black level and dynamic range, and selectable perceptual color maps. The iPad implementation uploads waterfall history as an image texture for asynchronous compositing; Android uses its hardware-accelerated Compose canvas. See [`docs/PANADAPTER_DESIGN.md`](docs/PANADAPTER_DESIGN.md).

## Shared core tests

```sh
cmake -S core -B core/build
cmake --build core/build
core/build/rigweave_core_tests
```

## Current physical status

- iPad target detected previously: iPad Pro 11-inch (4th generation), `iPad14,4`, iPadOS 26.6.1, Developer Mode enabled.
- The signed iPad app and embedded CP210x DriverKit extension build and verify locally.
- Portable feature-core tests and the signed iPad device build pass with the cluster, DX, Wavelog, callbook, WSJT-X, and panadapter code included.
- Physical install, DriverKit activation, USB enumeration, and live KX3/KX2 CAT responses remain unproven until the iPad is connected and unlocked.
- Live service credentials and endpoints have not been exercised in this checkout; those checks require the owner's configured accounts and network context.
- No Android hardware or emulator proof has been completed in this repository.
