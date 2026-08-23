# Generic radio surface

`HamlibGenericRadioSurface` is a standalone Compose component driven only by a model descriptor, a radio snapshot, connection/read-only state, and an `onAction` callback. It has no JNI calls and no direct transport access.

The compact primary block shows connection, frequency, modes, and VFOs. Secondary content is emitted only for reported capabilities: readable level chips and writable level sliders. Slider updates are safe to coalesce in the command queue. Edge actions and transmit actions are never coalesced or retried.

`HamlibModelSelector` provides bounded text search and orders favorites/recents before the remaining model catalogue. Selection does not connect, request USB permission, or transmit.

PTT is not rendered as an incidental toggle. The surface states that it is a separate transmit action; adoption by a product screen must add its own deliberate transmit-authority and physical acceptance policy.
