# Web Agent relay security M8

- Transport: TLS 1.3 or later over outbound WSS.
- Authentication: single-use Hosted challenge signed by a dedicated P-256 private key in the platform vault.
- Authorization: Hosted tenant role/capability intersection plus Agent-local authority checks.
- Isolation: one active Agent generation per station; reconnects require a new challenge.
- Bounds: 64 KiB control frames, explicit method allow-list, bounded media subscriptions, no inbound binary.
- Data minimization: no QSO bodies, messages, credentials, media, raw IQ, exact QTH, or local backups are persisted by Hosted.
- Failure: disconnect means no command delivery, no offline queue, invalid remote assumptions, TX disarmed by Agent policy.

Threats covered include replay, confused deputy, cross-tenant access, method smuggling, frame exhaustion, stale-generation control, generic-tunnel abuse, private-key disclosure, and Hosted compromise. A compromised Hosted service still cannot mint the Agent private-key signature or bypass Agent safety checks.
