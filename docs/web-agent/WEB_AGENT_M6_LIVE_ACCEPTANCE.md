# Web Agent M6 live acceptance

Source acceptance covers deterministic no-radio operation only. Core CTest, lifecycle/scale suites, Qt stationd compilation, authenticated workflow routing, idempotency, stale context, expiry, capability refusal, bounded rotator targets and Global Stop are required.

Not established by M6: physical radio connection, CAT/PTT/TUNE, RF transmission, live audio/IQ, physical rotator movement, real provider mutation, real Groups send, N1MM multi-operator networking, public listeners, production signing or release. These remain explicit M7 gates.

No long soak is part of M6. All debug services must be stopped after bounded checks.
