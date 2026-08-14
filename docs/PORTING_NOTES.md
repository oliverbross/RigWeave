# Porting Notes

| Original source | RigWeave destination | Treatment | Reason |
|---|---|---|---|
| `/Users/oliver/Documents/M5Stack Core2/kx3-tab5-remote/components/kx3_core` | `core/portable/{include,src}/kx3/{cat_parser,protocol,cty,spot}.*` | Copied owner-authored portable code | CAT parsing/protocol, CTY resolution, and cluster-spot parsing are standard C++ with no ESP-IDF dependency. |
| `/Users/oliver/Documents/M5Stack Core2/kx3-tab5-remote/components/adif` | `core/portable/{include,src}/kx3/adif.*` | Copied owner-authored portable code | Deterministic identity, ADIF, and Wavelog JSON payload generation. |
| `/Users/oliver/Documents/M5Stack Core2/kx3-tab5-remote/components/dx_analysis` | `core/portable/{include,src}/kx3/dx_analysis.*` | Copied owner-authored portable code | Bounded spot ranking, watchlists, solar state, band activity, and opportunities. |
| `/Users/oliver/Documents/M5Stack Core2/kx3-tab5-remote/components/operator_intel` | `core/portable/{include,src}/kx3/{operator_intel,wsjtx_protocol}.*` | Copied owner-authored portable code | Worked status, propagation context, and bounded WSJT-X parsing. |
| `/Users/oliver/Documents/M5Stack Core2/kx3-tab5-remote/components/panadapter` | `core/portable/{include,src}/kx3/panadapter_dsp.*` | Copied owner-authored portable code | FFT, windowing, smoothing, and I/Q metrics receive only physical PCM from the platform audio layer. |
| `/Users/oliver/Documents/M5Stack Core2/kx3-tab5-remote/components/sync_queue` | `core/portable/{include,src}/kx3/sync_queue.*` | Copied owner-authored portable code | HTTP outcome classification, URL normalization, and bounded retry policy. |
| Portable modules above | `core/src/features.cpp`, `core/include/rigweave/core.h` | Adapted | Stable C ABI used directly by Swift and JNI without importing embedded platform glue. |
| `/Users/oliver/Documents/Projects/OM0RX KX3 - Wavelog master/ios/CP210xDriver` | `ios/CP210xDriver` | Adapted | Existing owner DriverKit target and CP2102 match were rebundled under the RigWeave identifiers. |
| Tab5 visual/radio-state behaviour | `ios/RigWeave`, `android/app/src/main` | Redesigned | Native SwiftUI and Compose interfaces replace LVGL and ESP-IDF orchestration. |

No ESP-IDF USB host code, LVGL code, firmware output, credentials, personal logs, or managed third-party source was copied into this repository. TCP, UDP, HTTP, secure credential storage, audio capture, persistence, and UI are native platform implementations.
