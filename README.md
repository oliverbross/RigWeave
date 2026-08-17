# RigWeave Mobile

Native iPadOS and Android clients for direct Elecraft KX3/KX2 operation. The apps share a C++17 engine for CAT, ADIF, cluster spots, CTY resolution, worked-status intelligence, DX analysis, panadapter DSP, and Wavelog retry policy. They contain no demo or simulated radio state.

## iPadOS

Requirements:

- Xcode 26.6 or newer;
- Apple Development team `4WCMQ4U946`;
- installed profile `RigWeave iPad System Extension Development` for `app.rigweave.mobile`;
- installed DriverKit App Development profile `RigWeave CP210x DriverKit Development` for the legacy bundle ID `app.rigweave.mobile.CP210xDriver` (the extension itself now drives KXUSB/PL2303GC);
- supported M-series iPad with Developer Mode enabled;
- Elecraft KXUSB Prolific PL2303GC cable (`VID 067B`, `PID 23A3`) and KX3/KX2 configured for 38,400 baud, 8N1.

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

The host app embeds the signed DriverKit extension at `SystemExtensions/CP210xDriver.dext`. On iPadOS it uses base USBDriverKit for the KXUSB control/bulk transfers and an IOKit user-client channel to the app; it does not rely on macOS-only USBSerialDriverKit or `/dev` nodes. Connection begins only after tapping **Connect**, then the app polls the real KX3 identity, dual-VFO, mode, TX, meter, gain, bandwidth, power, preamp, attenuator, RIT/XIT, and split state. The KX3-inspired control deck and every CAT button operate on those responses; there is no simulated fallback.

The native iPad app also includes:

- a real TCP DX-cluster connection with callsign login, two ordered fallback clusters, watchlist ranking, CTY enrichment, band activity, and one-tap CAT tuning;
- live NOAA SFI/Kp retrieval;
- physical AVAudioSession input capture feeding the shared 1,024-bin panadapter DSP;
- local SQLite QSO logging in app-private tablet storage, searchable journal, and ADIF import/export through the system document picker;
- CTY.DAT download, validation, atomic replacement, and local callsign-prefix enrichment in app-private tablet storage;
- Wavelog connection/time checks, station discovery and selection, cursor-based full remote-log caching, and a durable upload queue with Keychain credentials, acknowledgement, quarantine, and bounded retry/backoff;
- the Tab5-derived graphical DX suite: live opportunities, smart ranking, bandmap, 12-bucket band pulse, world heat grid with regional activity, and watchlist activity;
- authenticated QRZ or HamQTH callbook lookup, connection testing, and log enrichment with credentials stored in the device Keychain.

Settings uses the same top-level order on iPadOS and Android: **Default, Log, Cluster, Macros, Alerts, Safety, Audio, Health, Diag, About**. WSJT-X is intentionally not exposed in this release.

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

The Android source also binds the full portable feature engine through JNI and supplies the same KX3-style live CAT deck, local ADIF journal/import/export, Wavelog discovery/upload/full-log cache, six graphical DX views, real TCP DX-cluster, NOAA, and `AudioRecord` physical-input panadapter paths with matching Compose screens. Android SDK licences were accepted with the owner's explicit approval and NDK `28.2.13676358` is installed under the SDK used by Gradle.

Android also includes six SSB voice-macro slots for exact USB/LSB operation. Recordings remain in private tablet storage, preview is verified against the built-in speaker, and transmission uses an explicitly selected DigiRig USB output with left-channel speech/right-channel silence. PTT is owned only by verified Elecraft CAT (`TQ0 -> TX -> TQ1 -> audio -> RX -> TQ0`); RTS/DTR are kept inactive. Multiple CAT or USB-audio candidates require explicit selection. See [`docs/VOICE_MACROS_ANDROID.md`](docs/VOICE_MACROS_ANDROID.md) for setup, privacy limits, calibration, and the operator-controlled dummy-load checklist.

Android EQ Studio is a dedicated responsive KX3 calibration workspace. It reads exact RX/TX menu values, separates verified radio state from local drafts and profiles, records one transient 10–15 second physical-input clip, supports raw-reference or hardware-baseline delta preview, and applies only after `TQ0`, menu/context, conflict, and exact readback checks. Expanded layouts expose EQ in the rail; compact layouts retain the six destinations and open it from Radio or Settings → Audio. See [`docs/EQ_STUDIO.md`](docs/EQ_STUDIO.md).

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

- Physical iPad: iPad Pro 11-inch (4th generation), `iPad14,4`, iPadOS 26.6.1, Developer Mode enabled and connected through CoreDevice.
- Signed build 38 and its embedded PL2303GC/KXUSB DriverKit extension build, install, launch, and verify on the physical iPad.
- Portable feature-core tests and the signed iPad device build pass with the cluster, DX, Wavelog, callbook, and panadapter code included.
- Physical CAT passes through the enabled DriverKit extension and real Elecraft KXUSB (`067B:23A3`): build 38 opened the PL2303GC at 38,400 8N1, identified the connected radio as KX3, and read VFO A at 21.1366 MHz. A physical UI test connected in Settings, navigated through Home and Radio, returned to Settings, and verified that CAT and frequency stayed live.
- Physical ICUSBAUDIO2D stereo I/Q passes at 48 kHz: more than 1.2 million live frames reached the shared 1,024-bin spectrum pipeline during the build-30 soak. The DSP now matches Tab5's proven analogue I/Q imbalance correction and complex-tone image-rejection test; every PCM frame is processed while SwiftUI presentation is bounded to about 9 FPS.
- The saved `OM0RX-6` profile was exercised against `cluster.om0rx.com:7300` on the physical iPad: login, `sh/dx 50`, parsing, shared-core snapshot generation, and populated Spots UI passed with real records. Wavelog authentication still requires a configured API key and station profile.
- Android voice-macro JVM tests and debug APK build are software evidence only. Connected-tablet launch and receive-only checks, plus every operator-controlled dummy-load/RF acceptance item, must be reported separately and must not be inferred from the build.
