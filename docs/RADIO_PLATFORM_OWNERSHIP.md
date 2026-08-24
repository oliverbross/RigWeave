# Radio and rotator ownership

| Resource | Single owner | Notes |
|---|---|---|
| Selected radio lifecycle | `RadioPlatformController` | Disconnect-before-create; restored selection is inert. |
| Android USB serial | `UsbRadioTransport` adapter | Exact selected/hashed identity; bounded reads; RTS/DTR deasserted unless a future reviewed adapter adds support. |
| Physical device identity | `PhysicalDeviceAuthority` | Shared by new radio and rotator runtimes; radio and rotator cannot claim the same identity concurrently. |
| KX radio | Existing NativeCore/USB path | Preserved; active only for native KX profiles. |
| FlexRadio | Existing `FlexRadioController` | Preserved; KX and integrated USB owners close before Flex selection. |
| QMX | `QmxConnectionController` | Existing reviewed command queue; no second Digi sequencer. |
| RGO ONE | `RgoOneConnectionController` | Existing reviewed scheduler and safety policy. |
| Hamlib radio | `HamlibConnectionController` | One session, one JNI handle, one Android bridge. |
| Rotator | `RotatorPlatformController` | One connected profile and one movement authority. |
| Digi transmit | Existing `DigiController` | New backends are blocked until they expose an integrated route through this authority. |
| Global Stop | `OperatorStopRouter` | Stops established TX workflows and requests rotator STOP/disarm. |

No backend opens Android serial devices from native code. JNI uses a socket bridge whose other end is serviced by the application-owned transport. No backend restores PTT, TUNE, Digi TX, rotator movement, park, automation arm or satellite tracking.
