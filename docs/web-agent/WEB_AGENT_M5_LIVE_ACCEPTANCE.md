# Web Agent M5 live acceptance

Local core acceptance uses deterministic fakes only. It covers lease lifecycle, local pre-emption, safe receive setters, capability refusal, idempotency, stale generations, readback, runtime limits, Global Stop and 10,000 commands.

The full Qt Agent cannot be compiled on the current Mac because Qt 6.11.2 is unavailable locally. Hosted CI is therefore the authority for the Station Agent build, Remote Station regression tests and sanitizer run.

No physical radio, serial/USB device, Hamlib endpoint, TCI server, Flex radio, audio device, rotator or RF path may be connected for M5 source acceptance. The user explicitly prohibited soaks, so the eight-hour integrated soak is not run and remains an acceptance blocker.
