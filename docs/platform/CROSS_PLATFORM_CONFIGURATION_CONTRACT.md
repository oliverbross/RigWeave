# Cross-platform configuration contract

The shared golden definition is `fixtures/platform/configuration_wavelog_golden.json`.

Portable when mapped safely: station identity, non-secret Wavelog binding metadata, cluster preferences, Band Map filters/presets, watchlists, model/profile metadata without device handles, portable preferences, theme and accessibility. Platform-local: USB/COM/DriverKit identity, window/QML layout, Android navigation, credential aliases and audio-device IDs.

Never export or restore credentials, PTT/TUNE, Digi transmit authority, active keyer/Contest/N1MM/DX sessions, live radio/rotator connection or motion, or pending commands. Restored radio and rotator state is disconnected and inert.

Unknown future sections are surfaced as `unknownSections` with `requiresReview`; selected unknown or unsafe sections fail explicitly. They are not silently dropped or granted authority.
