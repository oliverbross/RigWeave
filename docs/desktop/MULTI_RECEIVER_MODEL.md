# Multi-Receiver Model

ReceiverListModel is the one QML-facing receiver projection owned by DesktopRadioController. Hamlib projects exactly one hamlib:0 receiver. TCI projects up to eight stable tci:N rows and retains stale readback rows across a bounded reconnect.

Three roles are explicit:

- active-control receiver: frequency/mode mutations and the legacy single-receiver projection;
- listening receiver: the operator-selected receive/audio focus;
- transmit receiver: exactly one compatibility authority, never a new TX implementation.

For TCI, the controller attaches the union of active-control and listening receiver IDs and detaches receivers no longer in either role. Distinct roles therefore produce two concurrent I/Q streams; equal roles produce one. Changing a row never implicitly tunes, logs, transmits, publishes, or changes the canonical QSO owner.

Receiver removal revalidates all roles. The first remaining receiver is adopted when a selected row disappears. Disconnection marks rows stale and stops their stream states without fabricating new values. The controller remains transmit locked: pttAvailable and tuneAvailable are false for both Hamlib fixture and TCI operation.

Settings schema 2 persists active/listening IDs and TCI profiles while retaining unrelated legacy fields. Schema 1 host/port profiles migrate to explicit WebSocket endpoints. Future schemas and more than 32 profiles are rejected.
