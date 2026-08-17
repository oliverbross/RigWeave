# Actual feature inventory

Statuses: IMPLEMENTED, PARTIAL, DOCUMENTED_ONLY, DEFERRED, ABSENT, UNKNOWN. Evidence describes what was actually reviewed; it is not implied by a screen or class name.

## Product shell and radio

| Feature | Status | Shared core | Apple | Android | Evidence and paths | Gap/next |
|---|---|---|---|---|---|---|
| Native client shell | IMPLEMENTED | n/a | SwiftUI destinations and adaptive views in ContentView.swift | Compose destinations in MainActivity.kt | Source; both builds pass | Client destination coverage differs |
| Apple phone support | ABSENT/UNPROVEN | n/a | Xcode TARGETED_DEVICE_FAMILY=2, iOS 17 | n/a | Project config | Do not claim iPhone |
| KX3/KX2 parser/state | IMPLEMENTED | protocol.cpp, cat_parser.cpp, core.cpp | RadioModel bridge | NativeCore JNI | Core test/build; client builds | KX2 physical proof not current |
| Startup polling/identity/VFO/mode | IMPLEMENTED | startup commands and state C ABI | DriverKit transport | UsbRadioTransport | Source; historical iPad KX3 proof | Android physical proof |
| Split/RIT/XIT/meters/gain/BW/power/preamp/attenuator | IMPLEMENTED/PARTIAL BY CLIENT | state model/CAT | UI and state paths | expanded KX3 console | Source/build | Reconfirm command coverage per real radio |
| CAT safety classification | IMPLEMENTED | ReadOnly, AbsoluteSet, EdgeTriggered, Transmit in protocol.cpp | deliberate UI actions | confirmation/session arms | Core source/tests | Keep transmit/edge operations non-retryable |
| Apple KXUSB | IMPLEMENTED | n/a | base USBDriverKit extension/user client | n/a | Source/build; historical physical PL2303GC/KX3 | Current device revalidation |
| Android USB serial | IMPLEMENTED | n/a | n/a | usb-serial-for-android transport | Source/build | Physical chip/radio matrix unverified |
| Connection/reconnect state | IMPLEMENTED/PARTIAL | parser reset/state | maintainConnection | transport/app controller | Source/build | Fault-injection and physical recovery evidence |

## Spectrum, audio, macros and EQ

| Feature | Status | Shared core | Apple | Android | Evidence and paths | Gap/next |
|---|---|---|---|---|---|---|
| Stereo I/Q DSP | IMPLEMENTED | panadapter_dsp.cpp and C ABI | AVAudioSession feed | JNI/native audio path | Core test; both builds | Measured calibration |
| Spectrum/waterfall | IMPLEMENTED | bins, dB bins, smoothing | SwiftUI render/history | Android render/audio paths | Source/build; historical physical iPad I/Q | Physical Android proof |
| CAT-coupled frequency axis | IMPLEMENTED | client receives CAT/audio data | Apple view | Android view | Source/build | Measure with known signal |
| I/Q reversal and display controls | IMPLEMENTED/PARTIAL | DSP/client settings | source path | source path | Source | Persistence/interface matrix |
| Audio selection/monitoring | PARTIAL | n/a | AVAudioSession choices | AudioMonitorController | Source/build | Device-specific physical proof |
| CW macro storage/editing | IMPLEMENTED | CAT safety primitives | local fields/editor | bounded rules/editor | Source/build | Apple arming consistency review |
| CW send safety | IMPLEMENTED/PARTIAL | transmit/edge classifications | deliberate text action and safety copy | first-send confirmation, session arm, clears | Source/build; no Phase 0 transmit | Physical safety test only when authorised |
| Voice macros/PTT audio routing | ABSENT | none | none | none | Exact source search | Phase 1C |
| RX/TX EQ and profiles | ABSENT | none | none | none | Exact source search | Phase 1B |

## Logging, enrichment and synchronisation

| Feature | Status | Shared core | Apple | Android | Evidence and paths | Gap/next |
|---|---|---|---|---|---|---|
| Local SQLite QSO journal | IMPLEMENTED | QSO identity helpers | QSOStore.swift | QsoDatabase.kt schema v6 | Source; Android instrumented test exists | Instrumented test not run in Phase 0 |
| QSO entry/history/filtering | IMPLEMENTED | ADIF helpers | SwiftUI log surfaces | paged/filterable Compose logbook | Source/build; Android unit tests | UI/device evidence differs |
| ADIF import/export | IMPLEMENTED | adif.cpp | document-directory flows | app-private flows | Core tests/source | Round-trip fixtures per client |
| Duplicate identity/worked status | IMPLEMENTED | operator_intel/adif | shared binding | DB indexes/shared binding | Source/tests | Multi-authority acceptance cases |
| QRZ/HamQTH | IMPLEMENTED, AUTH UNVERIFIED | provider-neutral enrichment limited | CallbookService, Keychain password | CallbookController, encrypted prefs | Source/build | Service credentials/terms/current responses |
| CTY.DAT | IMPLEMENTED | parser/resolver | download/update | validated update/rollback controller | Core tests; client source | Data permission/notice for distribution/cache |
| Wavelog station/cache/upload queue | IMPLEMENTED, AUTH UNVERIFIED | normalisation/retry policy | Keychain queue/full sync | durable queue/two-way sync | Source/build | Authenticated read/write/duplicate/quarantine proof |
| Credential storage | IMPLEMENTED/PARTIAL | n/a | Keychain for API/password; username/defaults | Android Keystore-backed encryption in preferences | Source | Threat-model/release review |
| Direct QRZ/Club Log/eQSL upload | ABSENT | none | none | none | Source search | Phase 4, authority-safe |

## DX and network intelligence

| Feature | Status | Shared core | Apple | Android | Evidence and paths | Gap/next |
|---|---|---|---|---|---|---|
| TCP cluster and spot parsing | IMPLEMENTED | spot/dx analysis | configured cluster/Spots | FeatureController/Spots | Core tests; historical real iPad cluster | Current Android/live fallback evidence |
| Watchlists/tune actions | IMPLEMENTED/PARTIAL | operator intelligence | compact DX actions | expanded actions | Source/build | Transmit-safe/physical tune evidence |
| NOAA/solar | IMPLEMENTED | solar context | NOAA summary fetch | NOAA fetch | Source/build | Live-service proof/terms |
| LIVE/SMART/BANDMAP/PULSE/WORLD/WATCH | IMPLEMENTED ON ANDROID | shared primitives | not equivalent | NeuralDxScreen/Controller | Source, unit rules, Android build | Provider-by-provider live/device proof |
| Native maps | IMPLEMENTED ON ANDROID | geo helpers | no equivalent | MapLibre ESRI/CARTO/OSM attribution | Source/build | Tile/service terms and runtime attribution review |
| PSK Reporter/WSPR | IMPLEMENTED/PARTIAL ON ANDROID | parsing/ranking pieces | absent | live adapters/caches | Source/build | Auth/live rate/terms evidence |
| Satellite/weather/briefing/beacons | IMPLEMENTED/PARTIAL ON ANDROID | limited | absent/minor weather label only | provider adapters/cache/maps | Source/build | Provider maturity and terms; no universal-success claim |
| AI insight/Perplexity | OPTIONAL/PARTIAL ON ANDROID | none | absent | credentialed API path | Source only | User key, privacy, terms, cost and error evidence |
| Notifications/ntfy | IMPLEMENTED/PARTIAL ON ANDROID | none | absent | local notifications/optional endpoint | Source/build | Device and service proof |
| WSJT-X parsing/surface | PARTIAL | bounded parser implemented | parser binding; no top-level current claim | no current top-level claim | Core tests/source | Separately authorised UI/integration |

## Portable programmes, radios and desktop

| Feature | Status | Evidence | Gap/next |
|---|---|---|---|
| POTA/SOTA/WWFF logging fields | PARTIAL | Android QSO fields/filtering | Not programme support |
| Programme databases/feeds/chase | ABSENT | No exact implementation | Phase 2 |
| Activation sessions/P2P/S2S/spotting | ABSENT | No exact implementation | Phase 3 |
| Awards/needs/progress | ABSENT as portable workflow | General worked intelligence only | Phase 4 |
| FlexRadio/SmartLink/VITA/DAX | ABSENT | No exact source hit | Phase 5 |
| QMX/Hamlib/rigctld | ABSENT | No exact source hit | Phase 7 |
| macOS/Windows/Linux desktop | ABSENT | No Qt/CMake desktop project | Phase 6 |

## Tests and evidence

| Suite/evidence | Current result | Proves | Does not prove |
|---|---|---|---|
| core/test/core_tests.cpp | PASS 1/1, Debug host | shared API/parser/DSP/policy assertions compile and run | radio/audio hardware |
| Android JVM tests | PASS via testDebugUnitTest | pure Kotlin rules/controllers under unit coverage | instrumented SQLite/device/USB/audio |
| Android instrumented test | EXISTS, not run | potential database-on-Android coverage | no current execution proof |
| Android assembleDebug | PASS | Kotlin/JNI/native ABIs/package assemble | emulator/device/radio |
| Apple generic iOS build | PASS | app/DriverKit compile, embed and sign | install, activation, device/radio/audio |
| Apple hardware UI tests | EXIST | reusable physical acceptance procedures | not executed in Phase 0 |
| Historical iPad report | clearly historical | prior KX3/KXUSB/IQ/cluster observations | current Android, current services, future releases |
