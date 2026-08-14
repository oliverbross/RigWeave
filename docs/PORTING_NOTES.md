# Porting Notes

| Original source | RigWeave destination | Treatment | Reason |
|---|---|---|---|
| `/Users/oliver/Documents/M5Stack Core2/kx3-tab5-remote/components/kx3_core` | `core/src/core.cpp`, `core/include/rigweave/core.h` | Adapted | Bounded Elecraft CAT framing and `ID`, `FA`, `MD`, `IF`, `TQ` radio-state parsing behind a narrow C ABI. |
| `/Users/oliver/Documents/M5Stack Core2/kx3-tab5-remote/components/adif` | `core/src/core.cpp` | Adapted | Deterministic QSO identity and basic ADIF serialization without embedded storage dependencies. |
| `/Users/oliver/Documents/Projects/OM0RX KX3 - Wavelog master/ios/CP210xDriver` | `ios/CP210xDriver` | Adapted | Existing owner DriverKit target and CP2102 match were rebundled under the RigWeave identifiers. |
| Tab5 visual/radio-state behaviour | `ios/RigWeave`, `android/app/src/main` | Redesigned | Native SwiftUI and Compose interfaces replace LVGL and ESP-IDF orchestration. |

No ESP-IDF USB host code, LVGL code, firmware output, credentials, personal logs, or managed third-party source was copied into this repository.

