# Web Agent journal

The observer journal is a bounded 256-entry newest-first list of timestamp, event, and sanitized detail. It records service lifecycle, pairing offer/pending/approval/revocation, Hub identity creation, and observer session authentication/closure.

Journal detail may contain public device or session identifiers but never a private key, signature challenge, credential value, audio, IQ, spectrum payload, certificate body, or token. `--journal` is available only through the user-scoped local administration socket.
