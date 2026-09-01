# Web Agent remote RPC M8

Remote control uses framed JSON RPC messages, not a generic proxy. Every method is compiled into the Agent allow-list. Requests are bounded to 64 KiB, require authenticated relay state, and are dispatched into existing Agent safety/workflow authorities.

Allowed families are station health/session/stop, bounded radio and rotator control, audio and spectrum subscriptions, scanner, presets, measurements, recordings, workflow, Logbook, Sync, Groups, Intelligence, HamClock, and journal reads. Unknown methods, PTT/TUNE spellings, and all raw-IQ methods are rejected.

Hosted role and capability checks are necessary but not sufficient. The Agent rechecks active control windows, generation, interlocks, lease ownership, local preemption, TX policy, and Global Stop. Hosted cannot override a local denial.
