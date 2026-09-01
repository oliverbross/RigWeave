# Web Agent M5 compatibility

- Agent/Observer/Media protocols retained: 1.0.
- Domain Journal protocol retained: 1.
- Safe-control protocol introduced: 1.1.
- Unsupported or absent safe-control is an explicit unavailable state; legacy observers remain read-only.
- Web base: `64cf4ea46cab674d215629bf72853fa38caab2a3`.
- Native base: `beab47b175c235770d08788279fd0f2fe30b3041`.
- Frozen Android parity: `1e917fd2a0ec38a6b66eb9ab211d30ab48c331d3`.

Cross-repository acceptance must pin both candidate SHAs and compare protocol versions, capability schema and golden observer fixtures. It must reject safe-control against an older Agent rather than falling back to browser or Node authority.
