# Radio, Hamlib and rotator integration v1

Sweep 2 integrates the reviewed QMX/QMX+, RGO ONE, embedded Hamlib and rotator source histories into the Android application without changing `main`.

The central radio selection is now a `RadioConnectionProfile` containing a stable profile ID, model ID, backend kind, transport, bounded serial/network settings, read-only policy and optional Hamlib model number. Native profiles remain explicit and small; Hamlib's compiled registry supplies the long tail of models. The legacy `RadioFamily` enum is retained only for KX/Flex compatibility consumers.

Selection and connection are separate operations. Selecting or restoring a profile stops Digi RX, disarms transmit workflows, requests RX from the current owner, closes the old transport, and publishes a disconnected state. Only an explicit Connect action creates the chosen backend.

Native QMX validates a composite USB identity, requires CDC and UAC interface evidence, performs the reviewed handshake and exposes unknown capabilities as unknown. The Android adapter does not claim an I/Q route until the selected audio route proves the same stable digest. Native RGO ONE uses the reviewed V6 protocol controller; the conservative profile is read-only, and V6 model identity is verified after opening.

Embedded Hamlib enumerates the compiled registry and exposes a searchable generic profile selector. Serial models use the Android USB owner and bounded native bridge; network profiles require an explicit enabled endpoint. Hamlib's read-only flag is enforced in JNI as well as the application controller.

The rotator workspace instantiates native serial, rotctld and embedded-Hamlib drivers. Restored profiles do not connect. Motion and park require a fresh confirmation, automation is session-only, background clears the arm, and STOP bypasses ordinary movement review.

## Local candidate evidence

- The installed Android Hamlib 4.7.2 catalogue exposes 304 radio models from 37 backends. The earlier host enumeration recorded 302; the final Android registry is the release count. The byte-preservation watcher passed at commit `40f63488fe0bd751b147f48d62fd217bf53713a0` with 1,048 verified files.
- Android Kotlin compilation and 688 JVM tests passed. Android-test sources and APK packaging passed; final lint completed with 0 errors, 191 warnings and 40 hints.
- Rust passed 97 tests with one intentional ignore; the Debug native core passed 2/2 CTests; the unsigned iOS Simulator regression build passed.
- The arm64-only tablet APK is 58,293,188 bytes with SHA-256 `00b3c2eb7c6143d65e970d030ca096a48830d770c1cde76adc530a396d054be8`.
- The four-ABI debug AAB is 55,606,070 bytes with SHA-256 `29cab575b7d403a1876780806a397f5a73b69ec1ac11136e23a0da4bca8b414f`.
- Both archives pass the prohibited-payload audit: no rigctl/rigctld executable, duplicate `libc++`, P.533 payload, manual, firmware or private evidence.
- The QMX watcher reports review-required drift from the pinned v1.9.2 source to upstream v1.9.3; the pin was not changed during Sweep 2. RGO and Hamlib watchers are current. The private Radio Station Pro rotator source was unavailable; microHAM ARCO and Hamlib rotator pins report no change.

The protected Lenovo install used only `adb install -r` with the arm64 candidate. UID 10352, all 146 pre-launch non-cache file hashes, schema 16, 67,223 QSOs and 67,223 projections were preserved. The same PID survived 180 seconds and relaunch, the crash buffer stayed empty, and Radio, Settings and the empty Rotator workspace rendered while every backend remained disconnected. Exact hosted run identity is reported externally alongside the immutable SHA it validates. Physical QMX, RGO ONE and rotator acceptance remains pending and is not inferred from these checks.

See `RADIO_PLATFORM_OWNERSHIP.md`, `RADIO_PROFILE_AND_BACKEND_MATRIX.md`, `ROTATOR_INTEGRATION_AND_BAND_POLICY.md`, and `RADIO_ROTATOR_LIVE_ACCEPTANCE.md` for the detailed boundaries.
