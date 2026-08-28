# rigctld Protocol Matrix v6

Reference: Hamlib 4.7.2 `rigctld` protocol. The in-process compatibility server does not launch or package `rigctld`. It is OFF, loopback-only, and read-only by default.

| Operation | Classification |
|---|---|
| `dump_state`, `dump_caps` | SUPPORTED_READ |
| `get_freq`, `get_mode`, `get_vfo` | SUPPORTED_READ |
| `get_split_vfo`, `get_split_freq`, `get_split_mode` | SUPPORTED_READ |
| `get_rit`, `get_xit`, `get_ptt` | SUPPORTED_READ |
| `set_freq`, `set_mode` | SUPPORTED_SAFE_SET with local 30-second writer arm |
| remaining audited safe setters | DIALECT_SPECIFIC; parser-gated, owner unavailable returns `RPRT -7` |
| `set_ptt` | BLOCKED_BY_POLICY without full physical acceptance and separate TX arm |
| unsupported commands | `RPRT -4` |
| missing/bad arguments | `RPRT -1` |
| missing lease | `RPRT -8` |

Normal one-letter and long forms, the implemented extended separator form, CR/LF input, bounded pipelining, and reconnect are covered at parser/service level. Fixture results never establish WSJT-X, fldigi, radio, audio, or RF acceptance.

Primary reference: https://hamlib.sourceforge.net/html/rigctld.1.html
