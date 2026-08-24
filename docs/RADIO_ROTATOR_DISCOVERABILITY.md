# Radio and rotator discoverability

Settings → Radio begins with Radio Profiles. Built-in native-preferred KX3, KX2, FlexRadio, QMX, QMX+ and RGO ONE profiles and the searchable embedded Hamlib registry are visible. Choosing a profile persists the selection and disconnects existing owners; it never connects the new profile automatically.

Settings → Rotator exposes configured profiles, backend/protocol/transport, position truth, band-assignment count, automation state, safe profile creation, workspace navigation, connect/disconnect and delete. rotctld can be created with a review-required loopback endpoint; native serial, embedded Hamlib and ARCO compatibility require their explicit model/transport identity. Creation, editing and assignment never move hardware.

Only `RadioPlatformController` and `RotatorPlatformController` own their physical sessions. `PhysicalDeviceAuthority` prevents competing ownership. Restore remains disconnected/disarmed.
