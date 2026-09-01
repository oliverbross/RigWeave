# Web Agent safe control M5

The Station Agent remains the only radio authority. Safe-control protocol 1.1 adds an `OPERATOR` role, one renewable writer lease, local pre-emption, exact generations, idempotency, expiry, capability gates, readback and Global Stop. The Web Application Service can request operations but cannot fabricate acceptance.

The deterministic `--debug-no-radio` source exposes receive-only state. `PTT`, `TUNE`, transmit audio, transmit EQ, antenna switching, power changes, keying and rotator movement are rejected as forbidden operations. The private local admin socket uses user-only access and bounded base64url JSON requests; it is not a public radio-control endpoint.

Profiles cover KX2, KX3, Flex, QMX, QMX+, RGO ONE, TCI and Hamlib capability families. A profile advertises only what the Agent can safely execute. Unknown commands and unsupported capabilities fail closed.

Global Stop does not require a current writer lease. It stops scanner, recording, time shift, replay, measurement, monitor and calibration runtimes, releases the lease and increments the Agent generation so stale commands cannot replay.
