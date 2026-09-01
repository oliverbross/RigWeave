# Web Agent workflows M6

M6 adds workflow protocol 1.2 to the existing Station Agent. `workflow_control::Engine` owns operating-context generations, bounded idempotency, short-lived authority and deterministic result readback. `RemoteStationService` exposes `WORKFLOW_STATE` and authenticated `WORKFLOW` frames plus a user-only local stationd admin path.

The browser and Application Service never become hardware authorities. The Agent derives role and capability from its authenticated session and ignores browser claims. Global Stop clears the workflow arms alongside M5 control, radio and rotator owners.

Release builds do not enable deterministic fake actions. Physical TX, audio, radio, provider, Groups and rotator acceptance is deferred.
