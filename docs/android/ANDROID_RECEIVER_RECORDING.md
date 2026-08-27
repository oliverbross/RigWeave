# Android Receiver Recording

Recording is an explicit receive-only action. It writes mono 48 kHz PCM16 WAV in `files/local-receiver-recordings`, with a JSON sidecar containing bounded receiver/source/frequency/mode/filter/time/tone/RDS/note metadata. SQLite schema 1 stores only metadata and relative app-private filenames; audio blobs never enter the QSO database.

Limits are one active file, 30 minutes per file and 250 MB total by default, with operator-selectable lower caps. Startup removes partial files and incomplete rows; finalisation patches the WAV header, syncs, then atomically renames. Retention deletes oldest complete files and rows together. Share/export uses only the existing secure file authority; raw paths are never presented or included in support output.

Pre-roll comes from the existing `ReceiveTimeShiftController` audio extension, not a second owner. Scanner AUDIO capture requires explicit per-bank enablement and an already listening receiver. A persistent red recording state shows bytes and duration. Background, route loss, source/profile change, Global Stop, removal and close stop/finalise the active session.
