# macOS packaging proof

The cross-platform workflow builds the same Qt/QML source, installs an unsigned `.app`, and applies `macdeployqt` without signing or notarization. The macOS Keychain adapter remains a compiled interface/stub for the next desktop programme.
