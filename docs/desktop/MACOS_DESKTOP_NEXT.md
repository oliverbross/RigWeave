# macOS desktop next

The same Qt/QML source is built on `macos-15` with Qt 6.11.2 and AppleClang and packaged as an unsigned `.app` proof. This establishes source portability; it is not a signed, notarized or accepted macOS product.

Next work must wire the existing `DesktopCredentialVault` interface to Keychain, review menu/window conventions, sandbox/bookmark behavior, audio-device identity, accessibility, app bundle licences, signing/notarization and hardware/service acceptance. It must not replace or modify the native SwiftUI iPhone/iPad application or its Xcode project.
