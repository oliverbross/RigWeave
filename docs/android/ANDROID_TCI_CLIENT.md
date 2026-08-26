# Android TCI Client

`AndroidTciBackend` is a managed backend under the existing radio platform owner. It uses OkHttp WebSocket with an eight-second connect timeout, normal `wss` certificate validation, 15-second ping, an 8 MiB frame limit, and a bounded eight-frame decode queue.

Status parsing, binary validation, and command construction use the shared native C++ TCI contract. Unknown status commands are counted; malformed frames are rejected and counted. Receiver indices are stable for the connection generation.

Safe Android v1 actions are frequency, mode, receiver selection, listen selection, RX enable, mute, I/Q start/stop, and RX-audio start/stop. PTT, TUNE, TX audio, TX chrono, drive, and memory writes are blocked. Disconnect sends one bounded safe-stop sequence and clears stream state.
