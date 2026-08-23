# Native protocol matrix

| Protocol | Transport | Position | Absolute move | STOP | Elevation | Notes |
|---|---|---:|---:|---:|---:|---|
| ARCO configured GS-232A | serial/TCP | yes | yes | yes | profile dependent | Official ARCO external compatibility mode |
| ARCO configured DCU-1/Rotor-EZ | serial/TCP | yes | yes | yes | no | Two-command preset/start sequence; no blind retry |
| GS-232A/B/generic | serial/TCP | yes | yes | yes | variant | Bounded exact text framing; 0-450 profile ranges preserved |
| DCU-1/Rotor-EZ/RotorCard compatible | serial/TCP | yes | yes | yes | no | Native subset; model-specific extras remain Hamlib |
| EasyComm I/II | serial/TCP | yes | yes | yes | yes | Version-specific terminator |
| EasyComm III | serial/TCP | basic position | basic position | yes | yes | Only the reviewed common command subset |
| SPID ROT1PROG | serial/TCP bridge | yes | yes | yes | no | Exact 13-byte command and 5-byte response framing |
| SPID ROT2PROG | serial/TCP bridge | yes | yes | yes | yes | Exact 13-byte command and 12-byte response framing; 1/0.5/0.25 degree modes |
| Remote rotctld | TCP | yes | yes | yes | model | Extended response with mandatory exact `RPRT` |

The reviewed ROT1/ROT2 protocol has no CRC field; framing, length, sentinels and resolution are validated instead. SPID MD-01 extensions and ARCO SPID-HR 0.1-degree framing are not guessed. Park falls back to an explicit, validated profile park position only where a native command is unavailable.
