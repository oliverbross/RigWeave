# Android FlexRadio control — Nexus-derived Phase 5A

## Evidence status

Phase 5A adds Android source and build integration for FlexRadio LAN and SmartLink selected-slice control. Host Rust tests, four Android Rust ABI builds, JNI/CMake linking, Android unit tests and Debug APK assembly pass. The UI was installed and exercised on the Lenovo TB373FU. The owner's SmartLink username/password were authorised for a test, but the issued OAuth client ID was not present; the official endpoint rejected an authorize request before the credential form with `Missing required parameter: client_id`. Authenticated broker/direct-radio and physical FLEX operation are therefore not claimed.

`SMARTLINK PHYSICAL LOGIN NOT RUN — OWNER-APPROVED CONFIGURATION VALUES WERE NOT PRESENT IN THE CODEX WORKTREE`

No RF transmission was performed. No fake QSO or fake Flex radio was created. No Nexus TX/DAX orchestration was imported into the Phase 5A runtime.

## Provenance and build

The Rust crate ports and modifies only Nexus `flexdisc.rs` and the receive-control portion of `flexcat.rs` at `6ec4a7925f1550cc364c7fd95967ce38c696ad3f`. Exact ranges, symbols, changes, licence, dependency closure and excluded modules are in [`rust/rigweave-flex/UPSTREAM.md`](../rust/rigweave-flex/UPSTREAM.md). RigWeave and the imported source are GPL-3.0-only; `COPYING` and `NOTICE` apply.

Required tools:

```sh
rustup default stable
rustup target add armv7-linux-androideabi aarch64-linux-android i686-linux-android x86_64-linux-android
cargo install cargo-ndk --locked
cargo test --manifest-path rust/rigweave-flex/Cargo.toml
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Gradle builds `librigweave_flex.a` for `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`; CMake links the matching archive into the existing `librigweave.so`. Machine-specific SDK/NDK paths are not committed.

## Developer configuration and credentials

The owner already has official FlexRadio developer access. FlexRadio's April 2026 [SmartLink API Reference](https://www.flexradio.com/documentation/smartsdr-waveform-api-pdf/) supplies the fixed Auth0 domain, redirect, broker endpoint and protocol grammar. Copy [`flex-developer.properties.example`](../flex-developer.properties.example) to ignored `flex-developer.properties` at the repository root, or export. Only `FLEX_SMARTLINK_CLIENT_ID` is required with the published defaults; the other three names are optional tenant overrides:

```text
FLEX_SMARTLINK_CLIENT_ID
FLEX_SMARTLINK_AUTH_DOMAIN
FLEX_SMARTLINK_REDIRECT_URI
FLEX_SMARTLINK_SERVER
```

The username/password are operator credentials and are never build configuration. With the client ID missing the app shows `OWNER CONFIGURATION VALUES REQUIRED` and makes no Auth0 or broker call.

Authentication uses a restricted authentication-only WebView because the issued HTTPS redirect belongs to the Auth0 tenant. It uses the official implicit-token flow, a random state, exact HTTPS redirect origin/path matching, and destroys the WebView after completion. The short-lived ID token is memory-only. The refresh token is AES-GCM encrypted by an Android Keystore key and is cleared with Auth0 cookies on logout; credentials/tokens are not placed in SQLite, logs, screenshots or diagnostics.

The broker uses ordinary platform TLS, registers the application as its first message, consumes the pushed radio list, sends a ten-second broker keepalive, then requests one selected radio. The broker-selected direct radio socket alone accepts a currently valid self-signed leaf certificate; no global trust override exists. `wan validate` is written before any normal SmartSDR command. Phase 5A fails closed when the advertised ports require NAT hole-punching.

## LAN and radio state

Visible foreground discovery listens on reusable UDP 4992, accepts only valid discovery records through the Rust parser, deduplicates by serial and ages entries. Selecting a LAN record opens its advertised TCP API port. No service, wake lock, WorkManager job or startup connection is added.

The Rust protocol core incrementally frames bounded V/H/R/S/M lines, correlates command sequence replies and tracks nonzero handle, radio identity, GUI clients/stations, slice index/letter, ownership, `in_use`, active, observed TX, RF frequency, mode, filter edges/width and RX antenna metadata. Malformed lines are nonfatal and secrets are never logged.

RigWeave uses existing GUI stations and slices only. One compatible non-TX slice may auto-select; ambiguous state requires a tap. Preferred station identity persists, while raw slice indices do not survive reconnect. The runtime never creates/removes slices or disconnects another client.

## Receive-only boundary and workflow integration

Only Rust builders can emit client identity, radio/client/slice subscriptions, ping, existing-slice frequency, existing-slice mode and optional receive filter. There is no generic Flex command surface. The Phase 5A runtime contains no builder for TX/PTT/MOX/TUNE/CWX, microphone, remote audio, DAX, stream/display/slice creation or removal, TX-slice assignment, transmit settings, power, antenna or profiles.

The selected Flex slice maps into the existing `RadioState`; normal logger and POTA Activate therefore read its real model/frequency/mode. Existing DX, presets and Portable Tune/Tune & Log dispatch to the active backend: KX retains its CAT path, while Flex uses selected-slice receive builders. No selected slice means no Flex command; an unspecified/ambiguous mode changes frequency only. Progress and Sync calculations are unchanged.

Settings → Radio persists one active family. Flex selection closes KX USB polling and hides expanded KX EQ/panadapter destinations. Switching back closes Flex sockets and restores the existing KX path. Reconnect is operator-established only, limited to three increasing delays, and never retries a transmit operation.

## Android UI

The Radio destination becomes a Flex-specific responsive workspace with LAN/SmartLink identity, explicit connection states, radios, GUI stations, existing slices, large frequency, observed mode/filter/TX metadata, direct receive frequency/step/mode controls, Open Log, Disconnect and Logout. It contains no TX control or unlicensed FlexRadio/Nexus logo and shows the independent-GPL/Nexus attribution.

Physical Lenovo evidence is captured as [`flex-radio-selection.png`](screenshots/flex-radio-selection.png) and [`flex-smartlink-radios.png`](screenshots/flex-smartlink-radios.png). The second image truthfully shows the no-LAN/no-client-ID state and the receive-only boundary. `flex-station-slice-control.png` is absent because no authenticated real station/slice existed; no placeholder radio was created.

## Exclusions and next phases

Phase 5A excludes spectrum/waterfall, VITA FFT/meters, DAX, remote audio, all transmission, slice/display creation, iPadOS/desktop work, generic Hamlib/QMX abstractions, release/store work and broad lint cleanup. Phase 5B may import/adapt Nexus `flexvita.rs` for VITA FFT/meters after a fresh audit. Phase 5C may consider carefully separated RX-only audio; wholesale `flexdax.rs` remains rejected and TX stays later.
